package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La compra (US1) y la venta (US4) de punta a punta por HTTP, contra una H2 propia, con el administrador configurado
 * como superusuario y el reloj quieto el 28/10/2026. Kane cotiza 103,00 desde el lunes 26/10 y
 * tiene su orden inicial con los 100 tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    private static final String BUY_PATH = "/orders/buy";
    private static final String SELL_PATH = "/orders/sell";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_REQUEST = "El request tiene datos inválidos.";
    private static final String UNREADABLE_BODY = "El cuerpo del request no es un JSON válido.";
    private static final String QUANTITY_MESSAGE = "La cantidad tiene que ser un entero entre 1 y 100.";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final long MISSING_PLAYER = 999_999L;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:order-controller-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private MarketDatabase database;
    private Team bayern;
    private Player kane;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
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
    void comprarDiezContraLaInicialResponde201ConLaFormaDelContrato() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = buy(bearer(user), body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        String raw = raw(result);
        assertThat(raw).contains("\"tokenPrice\":1.0000", "\"total\":10.00", "\"value\":103.00",
                "\"score\":0.300000", "\"executedTotal\":10.00", "\"balance\":990.00", "\"availableBalance\":990.00",
                "\"reservedBalance\":0.00", "\"reservedCredits\":0.00");
        JsonNode execution = jsonMapper.readTree(raw);
        JsonNode order = execution.get("order");
        assertThat(order.get("id").asLong()).isPositive();
        assertThat(order.get("side").asString()).isEqualTo("BUY");
        assertThat(order.get("ownerType").asString()).isEqualTo("USER");
        assertThat(order.get("status").asString()).isEqualTo("EXECUTED");
        assertThat(order.get("requestedQuantity").asInt()).isEqualTo(10);
        assertThat(order.get("executedQuantity").asInt()).isEqualTo(10);
        assertThat(order.get("cancelledQuantity").asInt()).isZero();
        assertThat(order.get("pendingQuantity").asInt()).isZero();
        assertThat(order.get("createdAt").asString()).isEqualTo("2026-10-28T15:00:00Z");
        assertThat(order.get("player").get("id").asLong()).isEqualTo(kane.id());
        assertThat(order.get("player").get("name").asString()).isEqualTo("Harry Kane");
        assertThat(order.get("player").get("team").asString()).isEqualTo("FC Bayern München");
        assertThat(order.get("player").get("league").asString()).isEqualTo("PREMIER");
        assertThat(order.has("ownerId")).isFalse();
        assertThat(execution.get("trades").values()).singleElement().satisfies(trade -> {
            assertThat(trade.get("id").asLong()).isPositive();
            assertThat(trade.get("executedAt").asString()).isEqualTo("2026-10-28T15:00:00Z");
            assertThat(trade.get("quantity").asInt()).isEqualTo(10);
            assertThat(trade.get("counterparty").asString()).isEqualTo("ADMIN");
            assertThat(trade.get("quote").get("validFrom").asString()).isEqualTo("2026-10-26T03:00:00Z");
            assertThat(trade.get("quote").get("tokenPrice").decimalValue()).isEqualByComparingTo("1.0300");
            assertThat(trade.get("quote").get("strategy").get("name").asString()).isNotBlank();
            assertThat(trade.get("quote").get("strategy").get("version").asInt()).isPositive();
        });
        assertThat(execution.get("executedQuantity").asInt()).isEqualTo(10);
    }

    @Test
    void conTresEnLaInicialResponde201ConLaCompraParcialYSuReserva() throws Exception {
        buy(bearer(helper.registerUser()), body(kane.id(), "97"));
        TestUser user = helper.registerUser();

        MvcTestResult result = buy(bearer(user), body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        String raw = raw(result);
        assertThat(raw).contains("\"reservedCredits\":7.21", "\"executedTotal\":3.00", "\"balance\":997.00",
                "\"availableBalance\":989.79", "\"reservedBalance\":7.21");
        JsonNode order = jsonMapper.readTree(raw).get("order");
        assertThat(order.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(order.get("executedQuantity").asInt()).isEqualTo(3);
        assertThat(order.get("pendingQuantity").asInt()).isEqualTo(7);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "cantidad 0                      | {\"playerId\": %d, \"quantity\": 0}    | quantity",
            "cantidad negativa               | {\"playerId\": %d, \"quantity\": -1}   | quantity",
            "cantidad decimal                | {\"playerId\": %d, \"quantity\": 1.5}  | quantity",
            "cantidad mayor que 100          | {\"playerId\": %d, \"quantity\": 101}  | quantity",
            "sin cantidad                    | {\"playerId\": %d}                     | quantity",
            "sin jugador                     | {\"quantity\": 10, \"other\": %d}      | playerId",
            "jugador 0                       | {\"playerId\": 0, \"quantity\": 10, \"other\": %d} | playerId"})
    void unCuerpoInvalidoResponde400ConLasViolacionesYNoCreaNada(String description, String template, String field)
            throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = buy(bearer(user), template.formatted(kane.id()));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonNode error = json(result);
        assertThat(error.get("message").asString()).isEqualTo(INVALID_REQUEST);
        assertThat(error.get("path").asString()).isEqualTo(BUY_PATH);
        assertThat(error.get("violations").values()).isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.get("field").asString()).isEqualTo(field));
        assertNothingBought(user);
    }

    @Test
    void laCantidadFueraDeRangoInformaElMensajeDelContrato() throws Exception {
        MvcTestResult result = buy(bearer(helper.registerUser()), body(kane.id(), "101"));

        assertThat(json(result).get("violations").values())
                .extracting(violation -> violation.get("message").asString())
                .containsOnly(QUANTITY_MESSAGE);
    }

    @Test
    void sinCuerpoOConJsonInvalidoResponde400() throws Exception {
        String bearer = bearer(helper.registerUser());

        MvcTestResult withoutBody = mvc.post().uri(BUY_PATH).header(AUTHORIZATION_HEADER, bearer)
                .contentType(MediaType.APPLICATION_JSON).exchange();
        MvcTestResult invalidJson = buy(bearer, "{\"playerId\": ");

        assertThat(withoutBody.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(withoutBody).get("message").asString()).isEqualTo(UNREADABLE_BODY);
        assertThat(invalidJson.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(invalidJson).get("message").asString()).isEqualTo(UNREADABLE_BODY);
        assertThat(database.trades()).isEmpty();
    }

    @Test
    void sinCredencialResponde401() throws Exception {
        MvcTestResult result = mvc.post().uri(BUY_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(body(kane.id(), "10")).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(json(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(database.trades()).isEmpty();
    }

    @Test
    void elAdministradorRecibe403YNadaCambia() throws Exception {
        String adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        AppUser before = database.superuser();

        MvcTestResult result = buy(BEARER_PREFIX + adminToken, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(database.ordersOf(before)).singleElement()
                .satisfies(order -> assertThat(order.isInitial()).isTrue());
        assertThat(database.initialOrderOf(kane).pending()).isEqualTo(100);
        assertThat(database.superuser().getBalance()).isEqualByComparingTo(before.getBalance());
    }

    @Test
    void unJugadorInexistenteResponde404() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = buy(bearer(user), body(MISSING_PLAYER, "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(json(result).get("message").asString())
                .isEqualTo("No se encontró el jugador con id " + MISSING_PLAYER + ".");
        assertNothingBought(user);
    }

    @Test
    void sinSaldoSuficienteResponde409() throws Exception {
        AppUser poor = database.user("5.00");
        String token = helper.login(new TestUser(poor.getId(), poor.getUsername(), poor.getEmail(), "campeon2022", null));

        MvcTestResult result = buy(BEARER_PREFIX + token, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(result).get("message").asString())
                .isEqualTo("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");
        assertThat(database.ordersOf(poor)).isEmpty();
        assertThat(database.account(poor.getId()).getBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void unJugadorInactivoResponde409() throws Exception {
        Player muller = database.quoteDatabase().inactive(
                database.quoteDatabase().player("8302", "Thomas Müller", Position.MIDFIELDER, bayern));
        database.quote(muller, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();

        assertConflict(muller, "El jugador no se puede operar: está inactivo.");
    }

    @Test
    void unJugadorSinCotizacionResponde409() throws Exception {
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        initialOrderService.openMissing();

        assertConflict(musiala, "El jugador no se puede operar: todavía no tiene cotización.");
    }

    @Test
    void unJugadorSinOrdenInicialResponde409() throws Exception {
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        database.quote(musiala, "103.00", "0.300000", QUOTE_FROM);

        assertConflict(musiala, "El jugador no se puede operar: todavía no tiene su orden inicial.");
    }

    @Test
    void elUsuarioDelCuerpoSeIgnoraYLaOrdenEsDelDuenoDeLaCredencial() throws Exception {
        TestUser user = helper.registerUser();
        TestUser other = helper.registerUser();
        String withUserId = "{\"playerId\": %d, \"quantity\": 1, \"userId\": %d}".formatted(kane.id(), other.id());

        MvcTestResult result = buy(bearer(user), withUserId);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(database.ordersOf(database.account(user.id()))).hasSize(1);
        assertThat(database.ordersOf(database.account(other.id()))).isEmpty();
    }

    @Test
    void funcionaConTokenYConClaveDeApi() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult withToken = buy(bearer(user), body(kane.id(), "1"));
        MvcTestResult withApiKey = mvc.post().uri(BUY_PATH).header(API_KEY_HEADER, user.apiKey())
                .contentType(MediaType.APPLICATION_JSON).content(body(kane.id(), "1")).exchange();

        assertThat(withToken.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(withApiKey.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(json(withApiKey).get("balance").decimalValue()).isEqualByComparingTo("998.00");
        assertThat(database.ordersOf(database.account(user.id()))).hasSize(2);
    }

    @Test
    void venderContraUnaCompraPendienteResponde201ConContraparteUsuario() throws Exception {
        String holder = bearer(helper.registerUser());
        TestUser buyer = helper.registerUser();
        buy(holder, body(kane.id(), "100"));
        buy(bearer(buyer), body(kane.id(), "4"));

        MvcTestResult result = sell(holder, body(kane.id(), "4"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        String raw = raw(result);
        assertThat(raw).contains("\"tokenPrice\":1.0300", "\"total\":4.12", "\"executedTotal\":4.12",
                "\"balance\":904.12", "\"availableBalance\":904.12", "\"reservedBalance\":0.00");
        JsonNode execution = jsonMapper.readTree(raw);
        JsonNode order = execution.get("order");
        assertThat(order.get("side").asString()).isEqualTo("SELL");
        assertThat(order.get("ownerType").asString()).isEqualTo("USER");
        assertThat(order.get("status").asString()).isEqualTo("EXECUTED");
        assertThat(order.get("pendingQuantity").asInt()).isZero();
        assertThat(execution.get("trades").values()).singleElement().satisfies(trade -> {
            assertThat(trade.get("quantity").asInt()).isEqualTo(4);
            assertThat(trade.get("counterparty").asString()).isEqualTo("USER");
            assertThat(trade.has("buyerId")).isFalse();
        });
        assertThat(database.ordersOf(database.account(buyer.id())).getFirst().status()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void venderMientrasLaInicialTieneTokensResponde409ConLosQueLeQuedanAlAdministrador() throws Exception {
        TestUser user = helper.registerUser();
        String bearer = bearer(user);
        buy(bearer, body(kane.id(), "99"));

        MvcTestResult result = sell(bearer, body(kane.id(), "5"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(result).get("message").asString())
                .isEqualTo("Todavía no se puede vender: al administrador le queda 1 token de este jugador.");
        assertThat(json(result).get("path").asString()).isEqualTo(SELL_PATH);
        assertThat(database.ordersOf(database.account(user.id()))).hasSize(1);
    }

    @Test
    void venderMasQueLosTokensLibresResponde409() throws Exception {
        TestUser user = helper.registerUser();
        String bearer = bearer(user);
        buy(bearer, body(kane.id(), "100"));
        sell(bearer, body(kane.id(), "60"));

        MvcTestResult result = sell(bearer, body(kane.id(), "50"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(result).get("message").asString())
                .isEqualTo("Tokens insuficientes: tiene 40 tokens libres de este jugador.");
        assertThat(database.ordersOf(database.account(user.id()))).hasSize(2);
    }

    @Test
    void conUnaOrdenPendienteDelOtroLadoResponde409EnLosDosSentidos() throws Exception {
        String holder = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(holder, body(kane.id(), "100"));
        buy(buyer, body(kane.id(), "3"));

        MvcTestResult sellWithPurchase = sell(buyer, body(kane.id(), "1"));
        sell(holder, body(kane.id(), "5"));
        MvcTestResult buyWithSale = buy(holder, body(kane.id(), "1"));

        assertThat(sellWithPurchase.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(sellWithPurchase).get("message").asString())
                .isEqualTo("Ya tiene una orden de compra pendiente de este jugador.");
        assertThat(buyWithSale.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(buyWithSale).get("message").asString())
                .isEqualTo("Ya tiene una orden de venta pendiente de este jugador.");
    }

    @Test
    void elAdministradorNoVendeYRecibe403() throws Exception {
        String adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        AppUser before = database.superuser();

        MvcTestResult result = sell(BEARER_PREFIX + adminToken, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(database.ordersOf(before)).singleElement()
                .satisfies(order -> assertThat(order.isInitial()).isTrue());
        assertThat(database.initialOrderOf(kane).pending()).isEqualTo(100);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "cantidad 0                      | {\"playerId\": %d, \"quantity\": 0}    | quantity",
            "cantidad negativa               | {\"playerId\": %d, \"quantity\": -1}   | quantity",
            "cantidad decimal                | {\"playerId\": %d, \"quantity\": 1.5}  | quantity",
            "cantidad mayor que 100          | {\"playerId\": %d, \"quantity\": 101}  | quantity",
            "sin cantidad                    | {\"playerId\": %d}                     | quantity",
            "sin jugador                     | {\"quantity\": 10, \"other\": %d}      | playerId",
            "jugador 0                       | {\"playerId\": 0, \"quantity\": 10, \"other\": %d} | playerId"})
    void unaVentaConCuerpoInvalidoResponde400ConLasViolaciones(String description, String template, String field)
            throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = sell(bearer(user), template.formatted(kane.id()));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonNode error = json(result);
        assertThat(error.get("message").asString()).isEqualTo(INVALID_REQUEST);
        assertThat(error.get("path").asString()).isEqualTo(SELL_PATH);
        assertThat(error.get("violations").values()).isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.get("field").asString()).isEqualTo(field));
        assertNothingBought(user);
    }

    @Test
    void unaVentaSinCuerpoOConJsonInvalidoResponde400() throws Exception {
        String bearer = bearer(helper.registerUser());

        MvcTestResult withoutBody = mvc.post().uri(SELL_PATH).header(AUTHORIZATION_HEADER, bearer)
                .contentType(MediaType.APPLICATION_JSON).exchange();
        MvcTestResult invalidJson = sell(bearer, "{\"playerId\": ");

        assertThat(withoutBody.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(withoutBody).get("message").asString()).isEqualTo(UNREADABLE_BODY);
        assertThat(invalidJson.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(invalidJson).get("message").asString()).isEqualTo(UNREADABLE_BODY);
    }

    @Test
    void unaVentaDeUnJugadorInexistenteResponde404() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = sell(bearer(user), body(MISSING_PLAYER, "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertNothingBought(user);
    }

    @Test
    void unaVentaSinCredencialResponde401() throws Exception {
        MvcTestResult result = mvc.post().uri(SELL_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(body(kane.id(), "10")).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(json(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
    }

    private MvcTestResult sell(String authorization, String body) {
        return mvc.post().uri(SELL_PATH).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private void assertConflict(Player player, String message) throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = buy(bearer(user), body(player.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(result).get("message").asString()).isEqualTo(message);
        assertNothingBought(user);
    }

    private void assertNothingBought(TestUser user) {
        assertThat(database.ordersOf(database.account(user.id()))).isEmpty();
        assertThat(database.account(user.id()).getBalance()).isEqualByComparingTo("1000.00");
    }

    private MvcTestResult buy(String authorization, String body) {
        return mvc.post().uri(BUY_PATH).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
    }

    private static String body(long playerId, String quantity) {
        return "{\"playerId\": %d, \"quantity\": %s}".formatted(playerId, quantity);
    }

    private static String raw(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(raw(result));
    }
}
