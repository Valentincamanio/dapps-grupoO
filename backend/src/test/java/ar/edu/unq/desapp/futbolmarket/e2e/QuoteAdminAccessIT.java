package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El disparo manual del recálculo de punta a punta: solo un administrador puede usarlo, y un
 * rechazo no cotiza nada (US1.16, SC-011).
 *
 * <p>Hay un partido con estadísticas en la última semana cerrada al momento de correr, así que un
 * recálculo permitido siempre cotiza algo. Usa un administrador al azar y una H2 propia, como
 * {@code SyncControllerIT}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuoteAdminAccessIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String RECALCULATE_PATH = "/quotes/recalculate";
    private static final String SIMULATE_PATH = "/quotes/simulate";
    private static final String SIMULATION_REQUEST = """
            {"parameters": {"defaultWeights": {"GOALS": 0.50, "RATING": 0.50}}}""";
    private static final String API_KEY_PATH = "/auth/me/api-key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final int PLAYERS = 2;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:quote-admin-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private QuoteDatabase database;
    private QuoteWeek lastClosed;

    /**
     * Los usuarios quedan entre tests: cada uno se registra con datos únicos.
     */
    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
        database = new QuoteDatabase(context);
        database.clean();
        lastClosed = QuoteWeek.lastClosedAt(Instant.now());
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Match match = database.finishedMatch("560600", lastClosed, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void sinCredencialResponde401YNoCotizaNada() throws Exception {
        MvcTestResult result = mvc.post().uri(RECALCULATE_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void unUsuarioComunConSuTokenRecibe403YNoCotizaNada() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri(RECALCULATE_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + helper.login(user))
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void unUsuarioComunConSuClaveDeApiRecibe403YNoCotizaNada() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri(RECALCULATE_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void unAdministradorConSuTokenRecibe200ConElInforme() throws Exception {
        MvcTestResult result = mvc.post().uri(RECALCULATE_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken())
                .exchange();
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(report.get("nothingToQuote").asBoolean()).isFalse();
        assertThat(report.get("strategy").get("name").asString()).isEqualTo("Rendimiento de partido");
        assertThat(report.get("strategy").get("version").asInt()).isOne();
        assertThat(report.get("quotedWeeks").values()).singleElement().satisfies(week -> {
            assertThat(week.get("weekStart").asString()).isEqualTo(lastClosed.monday().toString());
            assertThat(week.get("initial").asInt()).isEqualTo(PLAYERS);
            assertThat(week.get("calculated").asInt()).isEqualTo(PLAYERS);
            assertThat(week.get("requoted").asInt()).isZero();
        });
        assertThat(report.get("pendingWeeks").values()).isEmpty();
        assertThat(report.get("interruptedAt").isNull()).isTrue();
        assertThat(database.quoteCount()).isEqualTo(2L * PLAYERS);
    }

    @Test
    void unAdministradorConSuClaveDeApiRecibe200YRepetirDevuelveElInformeVacio() throws Exception {
        String apiKey = adminApiKey();
        mvc.post().uri(RECALCULATE_PATH).header(API_KEY_HEADER, apiKey).exchange();

        MvcTestResult second = mvc.post().uri(RECALCULATE_PATH).header(API_KEY_HEADER, apiKey).exchange();
        JsonNode report = body(second);

        assertThat(second.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("nothingToQuote").asBoolean()).isTrue();
        assertThat(report.get("quotedWeeks").values()).isEmpty();
        assertThat(database.quoteCount()).isEqualTo(2L * PLAYERS);
    }

    @Test
    void simularSinCredencialResponde401() throws Exception {
        MvcTestResult result = mvc.post().uri(SIMULATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIMULATION_REQUEST)
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void simularComoUsuarioComunResponde403() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri(SIMULATE_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + helper.login(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIMULATION_REQUEST)
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(database.quoteCount()).isZero();
    }

    private String adminToken() throws Exception {
        return helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    private String adminApiKey() throws Exception {
        MvcTestResult issued = mvc.post().uri(API_KEY_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken())
                .exchange();
        return body(issued).get("apiKey").asString();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
