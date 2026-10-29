package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Holding;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketMath;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El portfolio actual de punta a punta por HTTP (US2), contra una H2 propia, con el administrador
 * configurado como superusuario y el reloj quieto el 28/10/2026. Kane cotiza 103,00 desde el lunes
 * 26/10 y tiene su orden inicial con los 100 tokens.
 *
 * <p>Las compras a la orden inicial pasan por {@code POST /orders/buy}. Lo que todavía no se puede
 * hacer por la API (comprar a otro usuario y vender) se siembra con los repositories: las tenencias,
 * las órdenes y la operación de cada escenario, con la orden inicial descontando los tokens que
 * tienen los usuarios, así las invariantes del mercado se siguen cumpliendo.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-10-27T15:00:00Z");

    private static final String PORTFOLIO_PATH = "/users/%d/portfolio";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PASSWORD = "campeon2022";
    private static final String FORBIDDEN_MESSAGE = "No tiene permiso para ver la cuenta de otro usuario.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final long MISSING_USER = 999_999L;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:portfolio-controller-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private InitialOrderService initialOrderService;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private MarketOrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private AppUserRepository userRepository;

    @MockitoBean
    private WhoScoredAdapter whoScoredAdapter;

    @MockitoBean
    private FootballDataAdapter footballDataAdapter;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private MarketDatabase database;
    private Team bayern;
    private Player kane;
    private Quote kaneQuote;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
        database = new MarketDatabase(context);
        database.clean();
        bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        kaneQuote = database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void diezTokensCompradosALaInicialInformanCadaCampoDeLaPosicionYLosTotales() throws Exception {
        TestUser user = helper.registerUser();
        String bearer = bearer(user);
        buy(bearer, kane, 10);

        MvcTestResult result = portfolio(bearer, user.id());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        String raw = raw(result);
        assertThat(raw).contains("\"date\":null", "\"tokens\":10", "\"sharePercent\":10", "\"committed\":0",
                "\"averagePrice\":1.0000", "\"invested\":10.00", "\"tokenPrice\":1.0300", "\"score\":0.300000",
                "\"value\":10.30", "\"profit\":0.30", "\"profitPercent\":3.00", "\"balance\":990.00",
                "\"availableBalance\":990.00", "\"reservedBalance\":0.00", "\"realizedProfit\":0.00",
                "\"equity\":1000.30");
        JsonNode portfolio = jsonMapper.readTree(raw);
        assertThat(portfolio.get("userId").asLong()).isEqualTo(user.id());
        assertThat(portfolio.get("positions").values()).singleElement().satisfies(position -> {
            JsonNode player = position.get("player");
            assertThat(player.get("id").asLong()).isEqualTo(kane.id());
            assertThat(player.get("name").asString()).isEqualTo("Harry Kane");
            assertThat(player.get("team").asString()).isEqualTo("FC Bayern München");
            assertThat(player.get("league").asString()).isEqualTo("PREMIER");
            assertThat(player.get("position").asString()).isEqualTo("FORWARD");
            assertThat(player.get("active").asBoolean()).isTrue();
            assertThat(position.get("quote").get("validFrom").asString()).isEqualTo("2026-10-26T03:00:00Z");
            assertThat(position.get("quote").get("value").decimalValue()).isEqualByComparingTo("103.00");
            assertThat(position.get("quote").get("strategy").get("name").asString()).isNotBlank();
        });
        assertThat(portfolio.get("totals").get("invested").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(portfolio.get("totals").get("value").decimalValue()).isEqualByComparingTo("10.30");
        assertThat(portfolio.get("totals").get("profit").decimalValue()).isEqualByComparingTo("0.30");
    }

    @Test
    void dosComprasAPreciosDistintosPromedianLoPagado() throws Exception {
        AppUser ana = database.user("984.85");
        Holding holding = Holding.empty(ana.getId(), kane.id());
        holding.buy(10, new BigDecimal("10.00"));
        holding.buy(5, new BigDecimal("5.15"));
        seed(holding);

        JsonNode position = singlePosition(login(ana), ana);

        assertThat(position.get("tokens").asInt()).isEqualTo(15);
        assertThat(position.get("averagePrice").decimalValue()).isEqualByComparingTo("1.0100");
        assertThat(position.get("invested").decimalValue()).isEqualByComparingTo("15.15");
        assertThat(position.get("value").decimalValue()).isEqualByComparingTo("15.45");
    }

    @Test
    void venderNoCambiaElPromedioYAcumulaLaGananciaRealizada() throws Exception {
        AppUser ana = database.user("990.10");
        AppUser beto = database.user("994.75");
        Holding anaHolding = Holding.empty(ana.getId(), kane.id());
        anaHolding.buy(10, new BigDecimal("10.00"));
        anaHolding.buy(5, new BigDecimal("5.15"));
        anaHolding.commit(5);
        anaHolding.sell(5, new BigDecimal("5.25"));
        Holding betoHolding = Holding.empty(beto.getId(), kane.id());
        betoHolding.buy(5, new BigDecimal("5.25"));
        seed(anaHolding, betoHolding);
        seedSale(beto, ana, 5, "1.0500", 15, "0.20");

        String bearer = login(ana);
        JsonNode position = singlePosition(bearer, ana);
        JsonNode totals = json(portfolio(bearer, ana.getId())).get("totals");

        assertThat(position.get("tokens").asInt()).isEqualTo(10);
        assertThat(position.get("averagePrice").decimalValue()).isEqualByComparingTo("1.0100");
        assertThat(position.get("invested").decimalValue()).isEqualByComparingTo("10.10");
        assertThat(totals.get("realizedProfit").decimalValue()).isEqualByComparingTo("0.20");
    }

    @Test
    void elPromedioSeReiniciaDespuesDeQuedarEnCero() throws Exception {
        AppUser ana = database.user("984.45");
        AppUser beto = database.user("989.70");
        Holding anaHolding = Holding.empty(ana.getId(), kane.id());
        anaHolding.buy(10, new BigDecimal("10.00"));
        anaHolding.commit(10);
        anaHolding.sell(10, new BigDecimal("10.30"));
        anaHolding.buy(5, new BigDecimal("5.25"));
        Holding betoHolding = Holding.empty(beto.getId(), kane.id());
        betoHolding.buy(10, new BigDecimal("10.30"));
        seed(anaHolding, betoHolding);

        JsonNode position = singlePosition(login(ana), ana);

        assertThat(position.get("tokens").asInt()).isEqualTo(5);
        assertThat(position.get("averagePrice").decimalValue()).isEqualByComparingTo("1.0500");
        assertThat(position.get("invested").decimalValue()).isEqualByComparingTo("5.25");
    }

    @Test
    void elTotalInformaLibreReservadoYPatrimonio() throws Exception {
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        database.quote(musiala, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
        AppUser ana = database.user("996.18");
        ana.reserve(new BigDecimal("6.18"));
        userRepository.save(ana);
        orderRepository.save(MarketOrder.buy(ana.getId(), kane.id(), 6, new BigDecimal("6.18"),
                new BigDecimal("1.0300"), EARLIER));
        seed(bought(ana, kane, 10, "10.00"), bought(ana, musiala, 10, "10.00"));

        MvcTestResult result = portfolio(login(ana), ana.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(raw(result)).contains("\"balance\":996.18", "\"availableBalance\":990.00",
                "\"reservedBalance\":6.18", "\"value\":20.60", "\"equity\":1016.78");
        assertThat(json(result).get("positions").values()).extracting(position -> position.get("player").get("name")
                .asString()).containsExactly("Harry Kane", "Jamal Musiala");
    }

    @Test
    void unJugadorQuePasoAInactivoSeValuaConSuUltimaCotizacion() throws Exception {
        Player muller = database.quoteDatabase().player("8302", "Thomas Müller", Position.MIDFIELDER, bayern);
        database.quote(muller, "100.00", "0.000000", QUOTE_FROM);
        database.quote(muller, "95.00", "-0.100000", EARLIER);
        initialOrderService.openMissing();
        AppUser ana = database.user("990.00");
        seed(bought(ana, muller, 10, "10.00"));
        database.quoteDatabase().inactive(muller);

        JsonNode position = singlePosition(login(ana), ana);

        assertThat(position.get("player").get("active").asBoolean()).isFalse();
        assertThat(position.get("quote").get("value").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(position.get("value").decimalValue()).isEqualByComparingTo("9.50");
        assertThat(position.get("profit").decimalValue()).isEqualByComparingTo("-0.50");
    }

    @Test
    void sinTenenciasLaListaEstaVaciaYElPatrimonioEsElSaldo() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = portfolio(bearer(user), user.id());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(raw(result)).contains("\"positions\":[]", "\"invested\":0.00", "\"value\":0.00",
                "\"profit\":0.00", "\"equity\":1000.00");
    }

    @Test
    void unUsuarioComunNoVeElPortfolioDeOtroYElAdministradorSi() throws Exception {
        TestUser owner = helper.registerUser();
        TestUser other = helper.registerUser();

        MvcTestResult fromOther = portfolio(bearer(other), owner.id());
        MvcTestResult fromAdmin = portfolio(adminBearer(), owner.id());

        assertThat(fromOther.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(fromOther).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(fromAdmin.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(fromAdmin).get("userId").asLong()).isEqualTo(owner.id());
    }

    @Test
    void unUsuarioInexistenteEs404ParaElAdministradorY403ParaUnUsuarioComun() throws Exception {
        MvcTestResult fromAdmin = portfolio(adminBearer(), MISSING_USER);
        MvcTestResult fromUser = portfolio(bearer(helper.registerUser()), MISSING_USER);

        assertThat(fromAdmin.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(json(fromAdmin).get("message").asString())
                .isEqualTo("No se encontró el usuario con id " + MISSING_USER + ".");
        assertThat(fromUser.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(fromUser).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
    }

    @Test
    void sinCredencialResponde401() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.get().uri(PORTFOLIO_PATH.formatted(user.id())).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(json(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
    }

    @Test
    void unIdentificadorInvalidoResponde400() throws Exception {
        String bearer = bearer(helper.registerUser());

        MvcTestResult zero = portfolio(bearer, 0L);
        MvcTestResult text = mvc.get().uri("/users/abc/portfolio").header(AUTHORIZATION_HEADER, bearer).exchange();

        assertThat(zero.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(text.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void elAdministradorVeLosTokensSinVenderDelSuperusuarioSinDatosDeCosto() throws Exception {
        buy(bearer(helper.registerUser()), kane, 10);
        long superuserId = database.superuser().getId();

        MvcTestResult result = portfolio(adminBearer(), superuserId);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode portfolio = json(result);
        assertThat(portfolio.get("userId").asLong()).isEqualTo(superuserId);
        assertThat(portfolio.get("positions").values()).singleElement().satisfies(position -> {
            assertThat(position.get("player").get("id").asLong()).isEqualTo(kane.id());
            assertThat(position.get("tokens").asInt()).isEqualTo(90);
            assertThat(position.get("value").decimalValue()).isEqualByComparingTo("92.70");
            assertThat(position.get("averagePrice").isNull()).isTrue();
            assertThat(position.get("invested").isNull()).isTrue();
            assertThat(position.get("profit").isNull()).isTrue();
            assertThat(position.get("profitPercent").isNull()).isTrue();
        });
        assertThat(portfolio.get("totals").get("realizedProfit").isNull()).isTrue();
    }

    @Test
    void cuatroUsuariosCompranCincoJugadoresYCadaUnoVeSusCincoPosiciones() throws Exception {
        List<Player> players = new ArrayList<>(List.of(kane));
        for (int index = 1; index < 5; index++) {
            Player player = database.quoteDatabase().player("830" + index, "Jugador " + index, Position.DEFENDER,
                    bayern);
            database.quote(player, "103.00", "0.300000", QUOTE_FROM);
            players.add(player);
        }
        initialOrderService.openMissing();
        List<TestUser> users = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            TestUser user = helper.registerUser();
            String bearer = bearer(user);
            for (Player player : players) {
                assertThat(buy(bearer, player, 2).getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
            }
            users.add(user);
        }

        for (TestUser user : users) {
            JsonNode portfolio = json(portfolio(bearer(user), user.id()));

            assertThat(portfolio.get("positions").values()).hasSize(5)
                    .allSatisfy(position -> assertThat(position.get("tokens").asInt()).isEqualTo(2));
            assertThat(portfolio.get("totals").get("invested").decimalValue()).isEqualByComparingTo("10.00");
            assertThat(portfolio.get("totals").get("value").decimalValue()).isEqualByComparingTo("10.30");
        }
    }

    @Test
    void conLasFuentesExternasCaidasRespondeIgualYNoLasConsulta() throws Exception {
        willThrow(new ExternalSourceException("sin respuesta")).given(footballDataAdapter).fetchLeague(any());
        willThrow(new ExternalSourceException("sin respuesta")).given(whoScoredAdapter).fetchLeagueTeams(any());
        willThrow(new ExternalSourceException("sin respuesta")).given(whoScoredAdapter).fetchMatchStats(anyString());
        TestUser user = helper.registerUser();
        String bearer = bearer(user);
        buy(bearer, kane, 10);

        MvcTestResult result = portfolio(bearer, user.id());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(raw(result)).contains("\"value\":10.30", "\"equity\":1000.30");
        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    @Test
    void unaFechaInvalidaFuturaOAnteriorAlRegistroResponde400ConElValor() throws Exception {
        TestUser user = helper.registerUser();
        String bearer = bearer(user);

        assertBadDate(portfolioAt(bearer, user.id(), "2026-13-01"),
                "La fecha '2026-13-01' no es válida: use el formato AAAA-MM-DD.");
        assertBadDate(portfolioAt(bearer, user.id(), "28/10/2026"),
                "La fecha '28/10/2026' no es válida: use el formato AAAA-MM-DD.");
        assertBadDate(portfolioAt(bearer, user.id(), "2026-10-29"), "La fecha 2026-10-29 es futura.");
        assertBadDate(portfolioAt(bearer, user.id(), "2026-10-27"),
                "La fecha 2026-10-27 es anterior al registro del usuario (2026-10-28).");
    }

    @Test
    void conFechaLaFormaNoTraeLibreReservadoNiComprometidos() throws Exception {
        TestUser user = helper.registerUser();
        String bearer = bearer(user);
        buy(bearer, kane, 10);

        MvcTestResult result = portfolioAt(bearer, user.id(), "2026-10-28");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        String raw = raw(result);
        assertThat(raw).contains("\"date\":\"2026-10-28\"", "\"tokens\":10", "\"averagePrice\":1.0000",
                "\"value\":10.30", "\"balance\":990.00", "\"realizedProfit\":0.00", "\"equity\":1000.30");
        assertThat(raw).doesNotContain("availableBalance", "reservedBalance", "committed");
        JsonNode position = json(result).get("positions").get(0);
        assertThat(position.get("quote").get("validFrom").asString()).isEqualTo("2026-10-26T03:00:00Z");
        assertThat(position.get("quote").get("score").decimalValue()).isEqualByComparingTo("0.300000");
    }

    @Test
    void unaCuentaSinFechaDeRegistroAdmiteFechasAnteriores() throws Exception {
        AppUser older = database.user("1000.00");

        MvcTestResult result = portfolioAt(login(older), older.getId(), "2026-01-15");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode portfolio = json(result);
        assertThat(portfolio.get("date").asString()).isEqualTo("2026-01-15");
        assertThat(portfolio.get("positions").size()).isZero();
        assertThat(portfolio.get("totals").get("balance").decimalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    void conFechaElPermisoSeVerificaAntesQueLaFecha() throws Exception {
        TestUser user = helper.registerUser();
        TestUser other = helper.registerUser();

        MvcTestResult result = portfolioAt(bearer(user), other.id(), "2026-13-01");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
    }

    private MvcTestResult portfolioAt(String authorization, long userId, String date) {
        return mvc.get().uri(PORTFOLIO_PATH.formatted(userId) + "?date=" + date)
                .header(AUTHORIZATION_HEADER, authorization).exchange();
    }

    private void assertBadDate(MvcTestResult result, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(message);
    }

    /**
     * Guarda las tenencias y descuenta sus tokens de la orden inicial de cada jugador, como si los
     * hubieran comprado ahí.
     */
    private void seed(Holding... holdings) {
        holdingRepository.saveAll(List.of(holdings));
        for (Holding holding : holdings) {
            MarketOrder initial = database.orders().stream()
                    .filter(order -> order.isInitial() && order.getPlayerId() == holding.getPlayerId())
                    .findFirst()
                    .orElseThrow();
            initial.fill(holding.getQuantity());
            orderRepository.save(initial);
        }
    }

    private static Holding bought(AppUser owner, Player player, int tokens, String paid) {
        Holding holding = Holding.empty(owner.getId(), player.id());
        holding.buy(tokens, new BigDecimal(paid));
        return holding;
    }

    /**
     * Una venta de Kane ya ejecutada entera: la orden de cada parte y la operación con la ganancia
     * realizada del vendedor.
     */
    private void seedSale(AppUser buyer, AppUser seller, int tokens, String tokenPrice, int sellerTokensBefore,
                          String realizedProfit) {
        BigDecimal price = new BigDecimal(tokenPrice);
        BigDecimal total = MarketMath.total(tokens, price);
        MarketOrder sell = MarketOrder.sell(seller.getId(), kane.id(), tokens, EARLIER);
        sell.fill(tokens);
        MarketOrder buyOrder = MarketOrder.buy(buyer.getId(), kane.id(), tokens, total, price, EARLIER);
        buyOrder.fill(tokens);
        buyOrder.consumeReservation(total);
        long sellId = orderRepository.save(sell).getId();
        long buyId = orderRepository.save(buyOrder).getId();
        tradeRepository.saveAll(List.of(new Trade(null, EARLIER, kane.id(), buyId, sellId, buyer.getId(),
                seller.getId(), OwnerType.USER, tokens, price, total, kaneQuote.id(), buyer.getBalance().add(total),
                buyer.getBalance(), seller.getBalance().subtract(total), seller.getBalance(), 0, tokens,
                sellerTokensBefore, sellerTokensBefore - tokens, new BigDecimal(realizedProfit))));
    }

    private JsonNode singlePosition(String bearer, AppUser owner) throws Exception {
        MvcTestResult result = portfolio(bearer, owner.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode positions = json(result).get("positions");
        assertThat(positions.size()).isEqualTo(1);
        return positions.get(0);
    }

    private MvcTestResult portfolio(String authorization, long userId) {
        return mvc.get().uri(PORTFOLIO_PATH.formatted(userId)).header(AUTHORIZATION_HEADER, authorization).exchange();
    }

    private MvcTestResult buy(String authorization, Player player, int quantity) {
        return mvc.post().uri("/orders/buy").header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": %d, \"quantity\": %d}".formatted(player.id(), quantity)).exchange();
    }

    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
    }

    private String login(AppUser account) throws Exception {
        return bearer(new TestUser(account.getId(), account.getUsername(), account.getEmail(), PASSWORD, null));
    }

    private String adminBearer() throws Exception {
        return bearer(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    private static String raw(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(raw(result));
    }
}
