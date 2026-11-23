package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las métricas de negocio de punta a punta (US2 de la 012): cada caso de uso por HTTP sube
 * exactamente su serie, y la respuesta es la misma que verifican los IT de cada feature. Los
 * contadores son acumulativos en el contexto cacheado, así que cada caso mide la diferencia
 * antes/después (research D12). El escenario es el de {@code OrderControllerIT}: Kane cotiza 103,00
 * y tiene su orden inicial con los 100 tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessMetricsIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    private static final String ORDERS = "futbolmarket.market.orders";
    private static final String LOGINS = "futbolmarket.auth.logins";
    private static final String REGISTRATIONS = "futbolmarket.auth.registrations";
    private static final String USERS_REGISTERED = "futbolmarket.users.registered";
    private static final String OPEN_ORDERS = "futbolmarket.market.orders.open";
    private static final String OWN_PREFIX = "futbolmarket.";
    private static final String OPERATION = "operation";
    private static final String RESULT = "result";
    private static final String SUCCESS = "success";
    private static final String REJECTED = "rejected";
    private static final String FAILURE = "failure";
    private static final String BUY = "buy";
    private static final String SELL = "sell";
    private static final String CANCEL = "cancel";

    private static final String BUY_PATH = "/orders/buy";
    private static final String SELL_PATH = "/orders/sell";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PASSWORD = "campeon2022";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String NOT_OWNED_MESSAGE = "La orden no pertenece al usuario.";
    private static final long MISSING_PLAYER = 999_999L;
    private static final long MISSING_ORDER = 999_999L;

    private static final Map<String, Set<String>> VOCABULARY = Map.of(
            "application", Set.of("futbol-market"),
            OPERATION, Set.of(BUY, SELL, CANCEL),
            RESULT, Set.of(SUCCESS, REJECTED, FAILURE, "blocked", "in_progress"),
            "trigger", Set.of("manual", "scheduled", "startup"),
            "source", Set.of("football_data", "whoscored"),
            "type", Set.of("full", "single_league"));

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:business-metrics-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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
    private MeterRegistry registry;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private MarketDatabase database;
    private Player kane;

    @BeforeEach
    void setUp() {
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
        database.assertInvariants();
        database.clean();
    }

    // --- órdenes ---

    @Test
    void unaCompraAceptadaSumaUnSuccess() throws Exception {
        String buyer = bearer(helper.registerUser());
        double before = orders(BUY, SUCCESS);

        MvcTestResult result = buy(buyer, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(orders(BUY, SUCCESS)).isEqualTo(before + 1);
    }

    @Test
    void unaVentaAceptadaSumaUnSuccess() throws Exception {
        String holder = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(holder, body(kane.id(), "100"));
        buy(buyer, body(kane.id(), "4"));
        double before = orders(SELL, SUCCESS);

        MvcTestResult result = sell(holder, body(kane.id(), "4"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(orders(SELL, SUCCESS)).isEqualTo(before + 1);
    }

    @Test
    void cancelarUnaOrdenAbiertaSumaUnSuccessYBajaLasAbiertasEnUna() throws Exception {
        String first = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        buy(first, body(kane.id(), "97"));
        long purchase = orderId(buy(buyer, body(kane.id(), "10")));
        double successBefore = orders(CANCEL, SUCCESS);
        double openBefore = gauge(OPEN_ORDERS);

        MvcTestResult result = cancel(buyer, purchase);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(orders(CANCEL, SUCCESS)).isEqualTo(successBefore + 1);
        assertThat(gauge(OPEN_ORDERS)).isEqualTo(openBefore - 1);
    }

    @Test
    void unaCompraSinSaldoSumaUnRejectedYRespondeLoMismoQueSiempre() throws Exception {
        AppUser poor = database.user("5.00");
        String token = helper.login(new TestUser(poor.getId(), poor.getUsername(), poor.getEmail(), PASSWORD, null));
        double before = orders(BUY, REJECTED);

        MvcTestResult result = buy(BEARER_PREFIX + token, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(message(result)).isEqualTo("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");
        assertThat(orders(BUY, REJECTED)).isEqualTo(before + 1);
    }

    @Test
    void unaCompraDeUnJugadorInexistenteSumaUnRejectedYRespondeLoMismoQueSiempre() throws Exception {
        String buyer = bearer(helper.registerUser());
        double before = orders(BUY, REJECTED);

        MvcTestResult result = buy(buyer, body(MISSING_PLAYER, "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(message(result)).isEqualTo("No se encontró el jugador con id " + MISSING_PLAYER + ".");
        assertThat(orders(BUY, REJECTED)).isEqualTo(before + 1);
    }

    @Test
    void cancelarUnaOrdenInexistenteSumaUnRejectedYRespondeLoMismoQueSiempre() throws Exception {
        String user = bearer(helper.registerUser());
        double before = orders(CANCEL, REJECTED);

        MvcTestResult result = cancel(user, MISSING_ORDER);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(message(result)).isEqualTo("No se encontró la orden con id " + MISSING_ORDER + ".");
        assertThat(orders(CANCEL, REJECTED)).isEqualTo(before + 1);
    }

    @Test
    void cancelarUnaOrdenAjenaSumaUnRejectedYRespondeLoMismoQueSiempre() throws Exception {
        String first = bearer(helper.registerUser());
        String buyer = bearer(helper.registerUser());
        String other = bearer(helper.registerUser());
        buy(first, body(kane.id(), "97"));
        long purchase = orderId(buy(buyer, body(kane.id(), "10")));
        double before = orders(CANCEL, REJECTED);

        MvcTestResult result = cancel(other, purchase);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(message(result)).isEqualTo(NOT_OWNED_MESSAGE);
        assertThat(orders(CANCEL, REJECTED)).isEqualTo(before + 1);
    }

    @Test
    void elAdministradorQueIntentaComprarRecibe403DelFiltroYNoSumaNada() throws Exception {
        String adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        double before = allOrders();

        MvcTestResult result = buy(BEARER_PREFIX + adminToken, body(kane.id(), "10"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(message(result)).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(allOrders()).isEqualTo(before);
    }

    // --- sesión y registro ---

    @Test
    void unLoginCorrectoSumaUnSuccess() throws Exception {
        TestUser user = helper.registerUser();
        double before = logins(SUCCESS);

        MvcTestResult result = login(user.username(), user.password());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(logins(SUCCESS)).isEqualTo(before + 1);
    }

    @Test
    void unaContrasenaIncorrectaYUnUsuarioInexistenteSumanUnFailureCadaUno() throws Exception {
        TestUser user = helper.registerUser();
        double before = logins(FAILURE);

        MvcTestResult wrongPassword = login(user.username(), "otraClave2022");
        MvcTestResult missingUser = login("inexistente_" + SUFFIX, PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(missingUser.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(logins(FAILURE)).isEqualTo(before + 2);
    }

    @Test
    void unLoginConCuerpoInvalidoNoSumaNada() {
        double successBefore = logins(SUCCESS);
        double failureBefore = logins(FAILURE);

        MvcTestResult result = login("", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(logins(SUCCESS)).isEqualTo(successBefore);
        assertThat(logins(FAILURE)).isEqualTo(failureBefore);
    }

    @Test
    void unRegistroExitosoSumaUnRegistroYUnUsuario() {
        double registrationsBefore = registrations();
        double usersBefore = gauge(USERS_REGISTERED);

        MvcTestResult result = register("nuevo" + SUFFIX, "nuevo" + SUFFIX + "@correo.com");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(registrations()).isEqualTo(registrationsBefore + 1);
        assertThat(gauge(USERS_REGISTERED)).isEqualTo(usersBefore + 1);
    }

    @Test
    void unRegistroDuplicadoNoSumaNada() throws Exception {
        TestUser existing = helper.registerUser();
        double registrationsBefore = registrations();
        double usersBefore = gauge(USERS_REGISTERED);

        MvcTestResult result = register(existing.username(), existing.email());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(registrations()).isEqualTo(registrationsBefore);
        assertThat(gauge(USERS_REGISTERED)).isEqualTo(usersBefore);
    }

    @Test
    void ningunaSeriePropiaTieneEtiquetasFueraDelVocabulario() {
        var tags = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith(OWN_PREFIX))
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .toList();

        assertThat(tags).isNotEmpty().allSatisfy(this::assertInVocabulary);
    }

    // --- apoyo ---

    private void assertInVocabulary(Tag tag) {
        assertThat(VOCABULARY).containsKey(tag.getKey());
        assertThat(VOCABULARY.get(tag.getKey())).contains(tag.getValue());
    }

    private double orders(String operation, String result) {
        return registry.get(ORDERS).tag(OPERATION, operation).tag(RESULT, result).counter().count();
    }

    private double allOrders() {
        return registry.get(ORDERS).counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private double logins(String result) {
        return registry.get(LOGINS).tag(RESULT, result).counter().count();
    }

    private double registrations() {
        return registry.get(REGISTRATIONS).counter().count();
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private MvcTestResult buy(String authorization, String body) {
        return mvc.post().uri(BUY_PATH).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult sell(String authorization, String body) {
        return mvc.post().uri(SELL_PATH).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult cancel(String authorization, long orderId) {
        return mvc.post().uri("/orders/%d/cancel".formatted(orderId)).header(AUTHORIZATION_HEADER, authorization)
                .exchange();
    }

    private MvcTestResult login(String username, String password) {
        return mvc.post().uri(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password))
                .exchange();
    }

    private MvcTestResult register(String username, String email) {
        return mvc.post().uri(REGISTER_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}"
                        .formatted(username, email, PASSWORD))
                .exchange();
    }

    private long orderId(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        return json(result).get("order").get("id").asLong();
    }

    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
    }

    private static String body(long playerId, String quantity) {
        return "{\"playerId\": %d, \"quantity\": %s}".formatted(playerId, quantity);
    }

    private String message(MvcTestResult result) throws Exception {
        return json(result).get("message").asString();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
