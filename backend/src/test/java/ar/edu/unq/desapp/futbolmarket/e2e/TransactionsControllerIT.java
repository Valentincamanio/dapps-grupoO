package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El historial de operaciones y las órdenes de un usuario de punta a punta por HTTP (US6), contra
 * una H2 propia, con el administrador configurado como superusuario. Kane cotiza 103,00 desde el
 * lunes 26/10 y tiene su orden inicial con los 100 tokens.
 *
 * <p>Todo el escenario pasa por la API, con el reloj avanzando un día entre pedidos: el 28/10 Ana
 * compra 60 a la inicial, el 29/10 Bruno compra los 40 restantes, el 30/10 Bruno deja una compra de
 * 10 y Ana le vende 4, y el 31/10 Ana le vende los otros 6 y deja una venta de 5 que cancela.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionsControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    private static final String TRANSACTIONS_PATH = "/users/%d/transactions";
    private static final String ORDERS_PATH = "/users/%d/orders";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_MESSAGE = "No tiene permiso para ver la cuenta de otro usuario.";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final long MISSING_USER = 999_999L;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:transactions-controller-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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
    private MutableClock clock;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private MarketDatabase database;
    private Player kane;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        helper = new AuthTestHelper(mvc);
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
    }

    @AfterEach
    void verifyAndClean() {
        clock.set(NOW);
        database.assertInvariants();
        database.clean();
    }

    @Test
    void elHistorialVaDeLaMasNuevaALaMasViejaConCadaCampo() throws Exception {
        Scenario scenario = scenario();

        MvcTestResult result = transactions(scenario.ana(), scenario.ana().id(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode page = json(result);
        List<JsonNode> content = page.get("content").values().stream().toList();
        assertThat(content).extracting(trade -> trade.get("executedAt").asString())
                .containsExactly("2026-10-31T15:00:00Z", "2026-10-30T15:00:00Z", "2026-10-28T15:00:00Z");
        JsonNode newest = content.getFirst();
        assertThat(newest.get("id").asLong()).isPositive();
        assertThat(newest.get("player").get("id").asLong()).isEqualTo(kane.id());
        assertThat(newest.get("player").get("name").asString()).isEqualTo("Harry Kane");
        assertThat(newest.get("player").get("team").asString()).isEqualTo("FC Bayern München");
        assertThat(newest.get("player").get("league").asString()).isEqualTo("PREMIER");
        assertThat(newest.get("side").asString()).isEqualTo("SELL");
        assertThat(newest.get("quantity").asInt()).isEqualTo(6);
        assertThat(newest.get("tokenPrice").decimalValue()).isEqualByComparingTo("1.0300");
        assertThat(newest.get("total").decimalValue()).isEqualByComparingTo("6.18");
        assertThat(newest.get("counterparty").asString()).isEqualTo("USER");
        assertThat(newest.get("orderId").asLong()).isEqualTo(scenario.anaSecondSale());
        JsonNode quote = newest.get("quote");
        assertThat(quote.get("validFrom").asString()).isEqualTo("2026-10-26T03:00:00Z");
        assertThat(quote.get("value").decimalValue()).isEqualByComparingTo("103.00");
        assertThat(quote.get("tokenPrice").decimalValue()).isEqualByComparingTo("1.0300");
        assertThat(quote.get("score").decimalValue()).isEqualByComparingTo("0.300000");
        assertThat(quote.get("strategy").get("name").asString()).isNotBlank();
        assertThat(quote.get("strategy").get("version").asInt()).isPositive();
        assertThat(newest.get("realizedProfit").decimalValue()).isEqualByComparingTo("0.18");
        assertThat(content.get(1).get("orderId").asLong()).isEqualTo(scenario.anaFirstSale());
        assertThat(content.get(1).get("realizedProfit").decimalValue()).isEqualByComparingTo("0.12");
        JsonNode oldest = content.getLast();
        assertThat(oldest.get("side").asString()).isEqualTo("BUY");
        assertThat(oldest.get("quantity").asInt()).isEqualTo(60);
        assertThat(oldest.get("tokenPrice").decimalValue()).isEqualByComparingTo("1.0000");
        assertThat(oldest.get("total").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(oldest.get("counterparty").asString()).isEqualTo("ADMIN");
        assertThat(oldest.get("orderId").asLong()).isEqualTo(scenario.anaPurchase());
        assertThat(oldest.has("realizedProfit")).isFalse();
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void laContraparteSeDiceSoloPorSuTipoYLasComprasNoTraenGanancia() throws Exception {
        Scenario scenario = scenario();

        MvcTestResult result = transactions(scenario.bruno(), scenario.bruno().id(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        String raw = raw(result);
        assertThat(raw).doesNotContain(scenario.ana().username(), "buyerId", "sellerId", "\"realizedProfit\"");
        List<JsonNode> content = json(result).get("content").values().stream().toList();
        assertThat(content).extracting(trade -> trade.get("counterparty").asString())
                .containsExactly("USER", "USER", "ADMIN");
        assertThat(content).extracting(trade -> trade.get("side").asString()).containsOnly("BUY");
        assertThat(content).extracting(trade -> trade.get("quantity").asInt()).containsExactly(6, 4, 40);
        assertThat(content.subList(0, 2)).extracting(trade -> trade.get("orderId").asLong())
                .containsOnly(scenario.brunoPendingPurchase());
    }

    @Test
    void losFiltrosSeCombinanYElRangoIncluyeLosDosDias() throws Exception {
        Scenario scenario = scenario();
        TestUser ana = scenario.ana();

        assertThat(quantities(ana, "?side=SELL")).containsExactly(6, 4);
        assertThat(quantities(ana, "?side=BUY")).containsExactly(60);
        assertThat(quantities(ana, "?playerId=" + kane.id())).containsExactly(6, 4, 60);
        assertThat(quantities(ana, "?playerId=" + MISSING_USER)).isEmpty();
        assertThat(quantities(ana, "?from=2026-10-30&to=2026-10-30")).containsExactly(4);
        assertThat(quantities(ana, "?from=2026-10-29")).containsExactly(6, 4);
        assertThat(quantities(ana, "?to=2026-10-29")).containsExactly(60);
        assertThat(quantities(ana, "?side=SELL&from=2026-10-31&playerId=" + kane.id())).containsExactly(6);
        assertThat(quantities(ana, "?side=BUY&from=2026-10-29")).isEmpty();
    }

    @Test
    void unRangoInvertidoUnaFechaInvalidaOUnFiltroNoAdmitidoSon400() throws Exception {
        TestUser user = helper.registerUser();

        assertBadRequest(transactions(user, user.id(), "?from=2026-09-10&to=2026-09-01"),
                "El inicio del rango (2026-09-10) es posterior al final (2026-09-01).");
        assertBadRequest(transactions(user, user.id(), "?from=2026-02-30"),
                "La fecha '2026-02-30' no es válida: use el formato AAAA-MM-DD.");
        assertBadRequest(transactions(user, user.id(), "?to=30/10/2026"),
                "La fecha '30/10/2026' no es válida: use el formato AAAA-MM-DD.");
        assertBadRequest(transactions(user, user.id(), "?side=HOLD"), INVALID_PARAMETERS);
        assertBadRequest(transactions(user, user.id(), "?playerId=0"), INVALID_PARAMETERS);
        assertBadRequest(transactions(user, user.id(), "?size=51"), INVALID_PARAMETERS);
        assertBadRequest(transactions(user, user.id(), "?page=-1"), INVALID_PARAMETERS);
    }

    @Test
    void elHistorialSeRecorrePorHojas() throws Exception {
        Scenario scenario = scenario();

        JsonNode first = json(transactions(scenario.ana(), scenario.ana().id(), "?page=0&size=2"));
        JsonNode second = json(transactions(scenario.ana(), scenario.ana().id(), "?page=1&size=2"));

        assertThat(first.get("content").size()).isEqualTo(2);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("hasPrevious").asBoolean()).isFalse();
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("content").values()).singleElement()
                .satisfies(trade -> assertThat(trade.get("quantity").asInt()).isEqualTo(60));
        assertThat(second.get("hasPrevious").asBoolean()).isTrue();
        assertThat(second.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void sinOperacionesLaHojaVieneVacia() throws Exception {
        TestUser user = helper.registerUser();

        JsonNode page = json(transactions(user, user.id(), ""));

        assertThat(page.get("content").size()).isZero();
        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    void elHistorialLoVenSuDuenoYElAdministrador() throws Exception {
        Scenario scenario = scenario();
        long anaId = scenario.ana().id();

        assertForbidden(transactions(scenario.bruno(), anaId, ""));
        assertForbidden(transactions(scenario.bruno(), MISSING_USER, ""));
        MvcTestResult byAdmin = mvc.get().uri(TRANSACTIONS_PATH.formatted(anaId))
                .header(AUTHORIZATION_HEADER, adminBearer()).exchange();
        assertThat(byAdmin.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(byAdmin).get("totalElements").asLong()).isEqualTo(3);
        MvcTestResult missing = mvc.get().uri(TRANSACTIONS_PATH.formatted(MISSING_USER))
                .header(AUTHORIZATION_HEADER, adminBearer()).exchange();
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(json(missing).get("message").asString())
                .isEqualTo("No se encontró el usuario con id " + MISSING_USER + ".");
        assertUnauthorized(mvc.get().uri(TRANSACTIONS_PATH.formatted(anaId)).exchange());
    }

    @Test
    void lasOrdenesVanDeLaMasNuevaALaMasViejaConSuEstadoYSusCantidades() throws Exception {
        Scenario scenario = scenario();

        MvcTestResult result = orders(scenario.ana(), scenario.ana().id(), "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode page = json(result);
        List<JsonNode> content = page.get("content").values().stream().toList();
        assertThat(content).extracting(order -> order.get("id").asLong()).containsExactly(scenario.anaCancelledSale(),
                scenario.anaSecondSale(), scenario.anaFirstSale(), scenario.anaPurchase());
        assertThat(content).extracting(order -> order.get("status").asString())
                .containsExactly("CANCELLED", "EXECUTED", "EXECUTED", "EXECUTED");
        JsonNode cancelled = content.getFirst();
        assertThat(cancelled.get("side").asString()).isEqualTo("SELL");
        assertThat(cancelled.get("ownerType").asString()).isEqualTo("USER");
        assertThat(cancelled.get("requestedQuantity").asInt()).isEqualTo(5);
        assertThat(cancelled.get("executedQuantity").asInt()).isZero();
        assertThat(cancelled.get("cancelledQuantity").asInt()).isEqualTo(5);
        assertThat(cancelled.get("pendingQuantity").asInt()).isZero();
        assertThat(cancelled.get("player").get("name").asString()).isEqualTo("Harry Kane");
        assertThat(cancelled.get("createdAt").asString()).isEqualTo("2026-10-31T15:00:00Z");
        assertThat(content.getLast().get("side").asString()).isEqualTo("BUY");
        assertThat(content.getLast().get("executedQuantity").asInt()).isEqualTo(60);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        JsonNode second = json(orders(scenario.ana(), scenario.ana().id(), "?page=1&size=3"));
        assertThat(second.get("content").values()).singleElement()
                .satisfies(order -> assertThat(order.get("id").asLong()).isEqualTo(scenario.anaPurchase()));
        assertThat(second.get("hasPrevious").asBoolean()).isTrue();
    }

    @Test
    void unaCompraParcialInformaLoEjecutadoLoPendienteYSuReserva() throws Exception {
        TestUser ana = helper.registerUser();
        TestUser bruno = helper.registerUser();
        buy(ana, 100);
        long purchase = orderId(buy(bruno, 10));
        sell(ana, 4);

        JsonNode order = json(orders(bruno, bruno.id(), "")).get("content").get(0);

        assertThat(order.get("id").asLong()).isEqualTo(purchase);
        assertThat(order.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(order.get("executedQuantity").asInt()).isEqualTo(4);
        assertThat(order.get("pendingQuantity").asInt()).isEqualTo(6);
        assertThat(order.get("reservedCredits").decimalValue()).isEqualByComparingTo("6.18");
    }

    @Test
    void lasOrdenesTienenLasMismasReglasDeAcceso() throws Exception {
        TestUser ana = helper.registerUser();
        TestUser bruno = helper.registerUser();
        buy(ana, 10);

        assertForbidden(orders(bruno, ana.id(), ""));
        assertForbidden(orders(bruno, MISSING_USER, ""));
        MvcTestResult byAdmin = mvc.get().uri(ORDERS_PATH.formatted(ana.id()))
                .header(AUTHORIZATION_HEADER, adminBearer()).exchange();
        assertThat(byAdmin.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(byAdmin).get("content").size()).isEqualTo(1);
        MvcTestResult missing = mvc.get().uri(ORDERS_PATH.formatted(MISSING_USER))
                .header(AUTHORIZATION_HEADER, adminBearer()).exchange();
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertUnauthorized(mvc.get().uri(ORDERS_PATH.formatted(ana.id())).exchange());
        assertBadRequest(orders(ana, ana.id(), "?size=0"), INVALID_PARAMETERS);
    }

    /**
     * El escenario de la clase, armado por la API con el reloj en el día de cada pedido.
     */
    private Scenario scenario() throws Exception {
        TestUser ana = helper.registerUser();
        TestUser bruno = helper.registerUser();
        long anaPurchase = orderId(buy(ana, 60));
        onDay(29);
        buy(bruno, 40);
        onDay(30);
        long brunoPurchase = orderId(buy(bruno, 10));
        long firstSale = orderId(sell(ana, 4));
        onDay(31);
        long secondSale = orderId(sell(ana, 6));
        long cancelledSale = orderId(sell(ana, 5));
        MvcTestResult cancel = mvc.post().uri("/orders/%d/cancel".formatted(cancelledSale))
                .header(AUTHORIZATION_HEADER, bearer(ana)).exchange();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return new Scenario(ana, bruno, anaPurchase, firstSale, secondSale, cancelledSale, brunoPurchase);
    }

    private void onDay(int day) {
        clock.set(Instant.parse("2026-10-%02dT15:00:00Z".formatted(day)));
    }

    private List<Integer> quantities(TestUser user, String query) throws Exception {
        MvcTestResult result = transactions(user, user.id(), query);
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return json(result).get("content").values().stream().map(trade -> trade.get("quantity").asInt()).toList();
    }

    private MvcTestResult transactions(TestUser requester, long userId, String query) throws Exception {
        return mvc.get().uri(TRANSACTIONS_PATH.formatted(userId) + query)
                .header(AUTHORIZATION_HEADER, bearer(requester)).exchange();
    }

    private MvcTestResult orders(TestUser requester, long userId, String query) throws Exception {
        return mvc.get().uri(ORDERS_PATH.formatted(userId) + query)
                .header(AUTHORIZATION_HEADER, bearer(requester)).exchange();
    }

    private MvcTestResult buy(TestUser user, int quantity) throws Exception {
        return order("/orders/buy", user, quantity);
    }

    private MvcTestResult sell(TestUser user, int quantity) throws Exception {
        return order("/orders/sell", user, quantity);
    }

    private MvcTestResult order(String path, TestUser user, int quantity) throws Exception {
        MvcTestResult result = mvc.post().uri(path).header(AUTHORIZATION_HEADER, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": %d, \"quantity\": %d}".formatted(kane.id(), quantity)).exchange();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        return result;
    }

    private long orderId(MvcTestResult result) throws Exception {
        return json(result).get("order").get("id").asLong();
    }

    private void assertBadRequest(MvcTestResult result, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(message);
    }

    private void assertForbidden(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
    }

    private void assertUnauthorized(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(json(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
    }

    /**
     * Cada pedido inicia sesión con el reloj del momento, así la credencial nunca está vencida.
     */
    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
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

    private record Scenario(TestUser ana, TestUser bruno, long anaPurchase, long anaFirstSale, long anaSecondSale,
                            long anaCancelledSale, long brunoPendingPurchase) {
    }
}
