package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las métricas de operaciones ejecutadas de punta a punta (US2 de la 013, SC-005 y
 * contracts/metrics.md §1): las seis series existen, cada pedido por HTTP suma exactamente sus
 * operaciones confirmadas en la serie de su contraparte, lo que no ejecuta operaciones no suma, y al
 * final las métricas coinciden con las operaciones guardadas en el mercado.
 *
 * <p>Los contadores son acumulativos en el contexto compartido con los e2e de Actuator, así que cada
 * caso compara contra una lectura previa de {@code /actuator/prometheus} como {@code ADMIN}. Kane
 * cotiza 103,00 y tiene su orden inicial con los 100 tokens, como en {@code BusinessMetricsIT}.</p>
 */
class MarketTradeMetricsIT extends ActuatorITSupport {

    private static final String TRADES = "futbolmarket_market_trades_total";
    private static final String TOKENS = "futbolmarket_market_tokens_traded_total";
    private static final String CREDITS = "futbolmarket_market_credits_traded_total";
    private static final List<String> SERIES = List.of(TRADES, TOKENS, CREDITS);
    private static final String INITIAL_ORDER = "counterparty=\"initial_order\"";
    private static final String USER = "counterparty=\"user\"";
    private static final double CENT = 0.005;

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String BUY_PATH = "/orders/buy";
    private static final String SELL_PATH = "/orders/sell";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PASSWORD = "campeon2022";
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private InitialOrderService initialOrderService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private String adminToken;
    private MarketDatabase database;
    private Team bayern;
    private Player kane;

    @BeforeEach
    void setUp() throws Exception {
        helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        database = new MarketDatabase(context);
        database.clean();
        bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void lasSeisSeriesExistenConSusDosContrapartes() throws Exception {
        List<String> samples = sampleLines();

        for (String series : SERIES) {
            for (String counterparty : List.of(INITIAL_ORDER, USER)) {
                assertThat(samples).as(series + " " + counterparty)
                        .filteredOn(line -> line.startsWith(series + "{") && line.contains(counterparty))
                        .hasSize(1);
            }
        }
    }

    @Test
    void unaCompraQueCruzaConLaOrdenInicialSumaEnInitialOrder() throws Exception {
        String buyer = bearer(helper.registerUser());
        Map<String, Double> before = snapshot();

        MvcTestResult result = buy(buyer, kane, 3);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        Trade trade = database.trades().getFirst();
        assertThat(database.trades()).hasSize(1);
        assertThat(delta(before, TRADES, INITIAL_ORDER)).isEqualTo(1);
        assertThat(delta(before, TOKENS, INITIAL_ORDER)).isEqualTo(3);
        assertThat(delta(before, CREDITS, INITIAL_ORDER)).isCloseTo(trade.total().doubleValue(), within(CENT));
        assertThat(SERIES).allSatisfy(series -> assertThat(delta(before, series, USER)).isZero());
    }

    @Test
    void unaCompraQueCruzaContraDosVentasDeUsuariosSumaLasDosEnUser() throws Exception {
        String firstSeller = bearer(helper.registerUser());
        String secondSeller = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(firstSeller, kane, 50);
        buy(secondSeller, kane, 50);
        sell(firstSeller, kane, 3);
        sell(secondSeller, kane, 4);
        Map<String, Double> before = snapshot();

        MvcTestResult result = buy(buyer, kane, 7);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        List<Trade> userTrades = tradesWith(OwnerType.USER);
        assertThat(userTrades).hasSize(2);
        assertThat(delta(before, TRADES, USER)).isEqualTo(2);
        assertThat(delta(before, TOKENS, USER)).isEqualTo(7);
        assertThat(delta(before, CREDITS, USER)).isCloseTo(totalOf(userTrades), within(CENT));
        assertThat(SERIES).allSatisfy(series -> assertThat(delta(before, series, INITIAL_ORDER)).isZero());
    }

    @Test
    void unaVentaPendienteNoSumaYCuentaUnaSolaVezCadaCruceParcialPosterior() throws Exception {
        String seller = bearer(helper.registerUser());
        String firstBuyer = bearer(helper.registerUser());
        String secondBuyer = bearer(helper.registerUser());
        buy(seller, kane, 100);
        Map<String, Double> beforeSale = snapshot();

        MvcTestResult sale = sell(seller, kane, 5);
        Map<String, Double> afterSale = snapshot();
        buy(firstBuyer, kane, 2);
        Map<String, Double> afterPartial = snapshot();
        buy(secondBuyer, kane, 3);

        assertThat(sale.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(SERIES).allSatisfy(series -> assertThat(delta(beforeSale, afterSale, series, USER)).isZero());
        assertThat(delta(afterSale, afterPartial, TRADES, USER)).isEqualTo(1);
        assertThat(delta(afterSale, afterPartial, TOKENS, USER)).isEqualTo(2);
        assertThat(delta(afterPartial, TRADES, USER)).isEqualTo(1);
        assertThat(delta(afterPartial, TOKENS, USER)).isEqualTo(3);
        assertThat(delta(beforeSale, TRADES, USER)).isEqualTo(2);
    }

    @Test
    void lasComprasYVentasRechazadasNoSumanOperaciones() throws Exception {
        AppUser poor = database.user("5.00");
        String poorToken = BEARER_PREFIX
                + helper.login(new TestUser(poor.getId(), poor.getUsername(), poor.getEmail(), PASSWORD, null));
        String withoutTokens = bearer(helper.registerUser());
        Player unquoted = database.quoteDatabase().player("8301", "Sin Cotización", Position.FORWARD, bayern);
        Map<String, Double> before = snapshot();

        MvcTestResult insufficient = buy(poorToken, kane, 10);
        MvcTestResult noTokens = sell(withoutTokens, kane, 1);
        MvcTestResult noQuote = buy(withoutTokens, unquoted, 1);

        assertThat(insufficient.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(noTokens.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(noQuote.getResponse().getStatus()).isBetween(400, 499);
        assertThat(database.trades()).isEmpty();
        assertUnchanged(before);
    }

    @Test
    void cancelarUnaOrdenPendienteNoSumaOperaciones() throws Exception {
        String first = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(first, kane, 100);
        long pending = orderId(buy(buyer, kane, 10));
        Map<String, Double> before = snapshot();

        MvcTestResult cancelled = mvc.post().uri("/orders/%d/cancel".formatted(pending))
                .header(AUTHORIZATION_HEADER, buyer).exchange();

        assertThat(cancelled.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertUnchanged(before);
    }

    @Test
    void alFinalDelEscenarioLasMetricasCoincidenConLasOperacionesGuardadas() throws Exception {
        Map<String, Double> before = snapshot();
        String holder = bearer(helper.registerUser());
        String other = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(holder, kane, 60);
        buy(other, kane, 40);
        sell(holder, kane, 6);
        buy(buyer, kane, 4);
        sell(other, kane, 9);
        buy(buyer, kane, 8);
        buy(holder, kane, 2);

        List<Trade> trades = database.trades();
        assertThat(trades).hasSizeGreaterThan(3);
        assertThat(delta(before, TRADES, INITIAL_ORDER) + delta(before, TRADES, USER)).isEqualTo(trades.size());
        assertThat(delta(before, TOKENS, INITIAL_ORDER) + delta(before, TOKENS, USER))
                .isEqualTo(trades.stream().mapToInt(Trade::quantity).sum());
        assertThat(delta(before, CREDITS, INITIAL_ORDER) + delta(before, CREDITS, USER))
                .isCloseTo(totalOf(trades), within(CENT));
        assertThat(delta(before, TRADES, INITIAL_ORDER)).isEqualTo(tradesWith(OwnerType.ADMIN).size());
        assertThat(delta(before, CREDITS, USER)).isCloseTo(totalOf(tradesWith(OwnerType.USER)), within(CENT));
    }

    @Test
    void sinCredencialLasMetricasDan401YConUnUsuarioDan403() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult anonymous = mvc.get().uri(PROMETHEUS_PATH).exchange();
        MvcTestResult asUser = mvc.get().uri(PROMETHEUS_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(asUser.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    // --- apoyo ---

    private void assertUnchanged(Map<String, Double> before) throws Exception {
        Map<String, Double> after = snapshot();
        for (String series : SERIES) {
            for (String counterparty : List.of(INITIAL_ORDER, USER)) {
                assertThat(delta(before, after, series, counterparty)).as(series + " " + counterparty).isZero();
            }
        }
    }

    /** Los valores de las seis series, con la clave {@code serie|contraparte}. */
    private Map<String, Double> snapshot() throws Exception {
        List<String> samples = sampleLines();
        return SERIES.stream()
                .flatMap(series -> List.of(INITIAL_ORDER, USER).stream().map(counterparty -> series + "|" + counterparty))
                .collect(Collectors.toMap(key -> key, key -> valueOf(samples, key.split("\\|"))));
    }

    private double delta(Map<String, Double> before, String series, String counterparty) throws Exception {
        return delta(before, snapshot(), series, counterparty);
    }

    private static double delta(Map<String, Double> before, Map<String, Double> after, String series,
            String counterparty) {
        String key = series + "|" + counterparty;
        return after.get(key) - before.get(key);
    }

    private static double valueOf(List<String> samples, String... parts) {
        return samples.stream()
                .filter(line -> line.startsWith(parts[0] + "{"))
                .filter(line -> Arrays.stream(parts).skip(1).allMatch(line::contains))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .sum();
    }

    private List<String> sampleLines() throws Exception {
        return mvc.get().uri(PROMETHEUS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange()
                .getResponse().getContentAsString().lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private List<Trade> tradesWith(OwnerType sellerType) {
        return database.trades().stream().filter(trade -> trade.sellerType() == sellerType).toList();
    }

    private static double totalOf(List<Trade> trades) {
        return trades.stream().map(Trade::total).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
    }

    private MvcTestResult buy(String authorization, Player player, int quantity) {
        return order(BUY_PATH, authorization, player, quantity);
    }

    private MvcTestResult sell(String authorization, Player player, int quantity) {
        return order(SELL_PATH, authorization, player, quantity);
    }

    private MvcTestResult order(String path, String authorization, Player player, int quantity) {
        return mvc.post().uri(path).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": %d, \"quantity\": %d}".formatted(player.id(), quantity))
                .exchange();
    }

    private long orderId(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        return body.get("order").get("id").asLong();
    }

    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
    }
}
