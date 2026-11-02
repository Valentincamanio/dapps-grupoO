package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import tools.jackson.databind.json.JsonMapper;

/**
 * La matriz de acceso de los ocho endpoints del mercado (research D24, SC-007): sin credencial, con
 * una credencial inválida, como el usuario dueño, como otro usuario, como el administrador y con una
 * clave de API. Ninguno de los rechazos cambia el estado del mercado.
 *
 * <p>El escenario pasa por la API: Ana compra los 100 tokens de Kane a la orden inicial y Bruno, el
 * dueño de los recursos que se piden, deja una compra de 5 pendiente. Ana es "otro usuario".</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketAccessIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final String INVALID_CREDENTIAL_MESSAGE = "La credencial es inválida.";
    private static final String ROLE_FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String ACCOUNT_FORBIDDEN_MESSAGE = "No tiene permiso para ver la cuenta de otro usuario.";
    private static final String ORDER_FORBIDDEN_MESSAGE = "La orden no pertenece al usuario.";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:market-access-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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
    private TestUser ana;
    private TestUser bruno;
    private long brunoPurchase;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        helper = new AuthTestHelper(mvc);
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
        ana = helper.registerUser();
        bruno = helper.registerUser();
        assertStatus(order("/orders/buy", Credential.BEARER, ana, 100), HttpStatus.CREATED);
        MvcTestResult pending = order("/orders/buy", Credential.BEARER, bruno, 5);
        assertStatus(pending, HttpStatus.CREATED);
        brunoPurchase = jsonMapper.readTree(pending.getResponse().getContentAsString()).get("order").get("id").asLong();
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void elListadoEsPublicoParaCualquierCredencialAunqueSeaInvalida() throws Exception {
        for (Credential credential : Credential.values()) {
            assertStatus(send(mvc.get().uri("/orders"), credential, bruno), HttpStatus.OK);
        }
        assertStatus(send(mvc.get().uri("/orders"), Credential.BEARER, null), HttpStatus.OK);
    }

    @Test
    void comprarEsSoloDeUsuariosComunesYLosRechazosNoCambianNada() throws Exception {
        String before = state();

        assertUnauthorized(order("/orders/buy", Credential.NONE, null, 1), MISSING_CREDENTIAL_MESSAGE);
        assertUnauthorized(order("/orders/buy", Credential.INVALID_BEARER, null, 1), INVALID_CREDENTIAL_MESSAGE);
        assertUnauthorized(order("/orders/buy", Credential.INVALID_API_KEY, null, 1), INVALID_CREDENTIAL_MESSAGE);
        assertForbidden(order("/orders/buy", Credential.BEARER, null, 1), ROLE_FORBIDDEN_MESSAGE);

        assertThat(state()).isEqualTo(before);
        assertStatus(order("/orders/buy", Credential.BEARER, bruno, 1), HttpStatus.CREATED);
        assertStatus(order("/orders/buy", Credential.API_KEY, bruno, 1), HttpStatus.CREATED);
    }

    @Test
    void venderEsSoloDeUsuariosComunesYLosRechazosNoCambianNada() throws Exception {
        String before = state();

        assertUnauthorized(order("/orders/sell", Credential.NONE, null, 1), MISSING_CREDENTIAL_MESSAGE);
        assertUnauthorized(order("/orders/sell", Credential.INVALID_BEARER, null, 1), INVALID_CREDENTIAL_MESSAGE);
        assertUnauthorized(order("/orders/sell", Credential.INVALID_API_KEY, null, 1), INVALID_CREDENTIAL_MESSAGE);
        assertForbidden(order("/orders/sell", Credential.BEARER, null, 1), ROLE_FORBIDDEN_MESSAGE);

        assertThat(state()).isEqualTo(before);
        assertStatus(order("/orders/sell", Credential.BEARER, ana, 1), HttpStatus.CREATED);
        assertStatus(order("/orders/sell", Credential.API_KEY, ana, 1), HttpStatus.CREATED);
    }

    @Test
    void cancelarEsSoloDelDuenoDeLaOrdenYLosRechazosNoCambianNada() throws Exception {
        String path = "/orders/%d/cancel".formatted(brunoPurchase);
        String before = state();

        assertUnauthorized(send(mvc.post().uri(path), Credential.NONE, null), MISSING_CREDENTIAL_MESSAGE);
        assertUnauthorized(send(mvc.post().uri(path), Credential.INVALID_BEARER, null), INVALID_CREDENTIAL_MESSAGE);
        assertUnauthorized(send(mvc.post().uri(path), Credential.INVALID_API_KEY, null), INVALID_CREDENTIAL_MESSAGE);
        assertForbidden(send(mvc.post().uri(path), Credential.BEARER, null), ROLE_FORBIDDEN_MESSAGE);
        assertForbidden(send(mvc.post().uri(path), Credential.BEARER, ana), ORDER_FORBIDDEN_MESSAGE);
        assertForbidden(send(mvc.post().uri(path), Credential.API_KEY, ana), ORDER_FORBIDDEN_MESSAGE);

        assertThat(state()).isEqualTo(before);
        assertStatus(send(mvc.post().uri(path), Credential.API_KEY, bruno), HttpStatus.OK);
    }

    @Test
    void elPortfolioElHistorialYLasOrdenesLosVenSuDuenoYElAdministrador() throws Exception {
        String before = state();
        for (String resource : List.of("portfolio", "transactions", "orders")) {
            String path = "/users/%d/%s".formatted(bruno.id(), resource);

            assertUnauthorized(send(mvc.get().uri(path), Credential.NONE, null), MISSING_CREDENTIAL_MESSAGE);
            assertUnauthorized(send(mvc.get().uri(path), Credential.INVALID_BEARER, null),
                    INVALID_CREDENTIAL_MESSAGE);
            assertUnauthorized(send(mvc.get().uri(path), Credential.INVALID_API_KEY, null),
                    INVALID_CREDENTIAL_MESSAGE);
            assertForbidden(send(mvc.get().uri(path), Credential.BEARER, ana), ACCOUNT_FORBIDDEN_MESSAGE);
            assertForbidden(send(mvc.get().uri(path), Credential.API_KEY, ana), ACCOUNT_FORBIDDEN_MESSAGE);
            assertStatus(send(mvc.get().uri(path), Credential.BEARER, bruno), HttpStatus.OK);
            assertStatus(send(mvc.get().uri(path), Credential.API_KEY, bruno), HttpStatus.OK);
            assertStatus(send(mvc.get().uri(path), Credential.BEARER, null), HttpStatus.OK);
        }

        assertThat(state()).isEqualTo(before);
    }

    @Test
    void laAuditoriaEsSoloDelAdministrador() throws Exception {
        String before = state();

        assertUnauthorized(send(mvc.get().uri("/audit"), Credential.NONE, null), MISSING_CREDENTIAL_MESSAGE);
        assertUnauthorized(send(mvc.get().uri("/audit"), Credential.INVALID_BEARER, null), INVALID_CREDENTIAL_MESSAGE);
        assertUnauthorized(send(mvc.get().uri("/audit"), Credential.INVALID_API_KEY, null),
                INVALID_CREDENTIAL_MESSAGE);
        assertForbidden(send(mvc.get().uri("/audit"), Credential.BEARER, bruno), ROLE_FORBIDDEN_MESSAGE);
        assertForbidden(send(mvc.get().uri("/audit"), Credential.API_KEY, bruno), ROLE_FORBIDDEN_MESSAGE);
        assertStatus(send(mvc.get().uri("/audit"), Credential.BEARER, null), HttpStatus.OK);

        assertThat(state()).isEqualTo(before);
    }

    /**
     * Cómo se presenta quien pide. Con {@code BEARER} y sin usuario, es el administrador.
     */
    private enum Credential { NONE, INVALID_BEARER, INVALID_API_KEY, BEARER, API_KEY }

    private MvcTestResult order(String path, Credential credential, TestUser user, int quantity) throws Exception {
        return send(mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": %d, \"quantity\": %d}".formatted(kane.id(), quantity)), credential, user);
    }

    private MvcTestResult send(MockMvcRequestBuilder request, Credential credential, TestUser user) throws Exception {
        return switch (credential) {
            case NONE -> request.exchange();
            case INVALID_BEARER -> request.header(AUTHORIZATION_HEADER, BEARER_PREFIX + "no-es-un-token").exchange();
            case INVALID_API_KEY -> request.header(API_KEY_HEADER, "clave-inexistente").exchange();
            case BEARER -> request.header(AUTHORIZATION_HEADER, bearer(user)).exchange();
            case API_KEY -> request.header(API_KEY_HEADER, user.apiKey()).exchange();
        };
    }

    /**
     * Todo lo que un pedido podría cambiar: las órdenes con sus cantidades y reservas, las
     * operaciones, la auditoría y los saldos de las cuentas del escenario.
     */
    private String state() {
        String orders = database.orders().stream()
                .map(order -> List.of(order.getId(), order.status(), order.getExecuted(), order.getCancelled(),
                        order.getReservedCredits()).toString())
                .collect(Collectors.joining(";"));
        String trades = database.trades().stream().map(Trade::id).toList().toString();
        String audit = database.auditEntries().stream().map(AuditEntry::id).toList().toString();
        String balances = Stream.of(database.account(ana.id()), database.account(bruno.id()), database.superuser())
                .map(MarketAccessIT::balanceOf)
                .collect(Collectors.joining(";"));
        return String.join("|", orders, trades, audit, balances);
    }

    private static String balanceOf(AppUser account) {
        return account.getId() + ":" + account.getBalance().toPlainString() + ":"
                + account.getReserved().toPlainString();
    }

    /**
     * Cada pedido inicia sesión en el momento. Sin usuario, como el administrador.
     */
    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user == null ? admin() : user);
    }

    private TestUser admin() {
        return new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null);
    }

    private static void assertStatus(MvcTestResult result, HttpStatus status) {
        assertThat(result.getResponse().getStatus()).isEqualTo(status.value());
    }

    private void assertUnauthorized(MvcTestResult result, String message) throws Exception {
        assertStatus(result, HttpStatus.UNAUTHORIZED);
        assertThat(message(result)).isEqualTo(message);
    }

    private void assertForbidden(MvcTestResult result, String message) throws Exception {
        assertStatus(result, HttpStatus.FORBIDDEN);
        assertThat(message(result)).isEqualTo(message);
    }

    private String message(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("message").asString();
    }
}
