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
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationLock;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El disparo manual del recálculo con otro recálculo o una sincronización en curso, de punta a
 * punta y sin hilos ni esperas (US6.5, US6.6, FR-049 y FR-050).
 *
 * <p>Los candados se toman a propósito corriendo el pedido adentro de su {@code runExclusively}:
 * MockMvc atiende en el mismo hilo y los candados no son reentrantes, así que el recálculo los
 * encuentra ocupados. Hay un partido con estadísticas en la última semana cerrada, así que un
 * recálculo permitido cotizaría algo. Usa un administrador al azar y una H2 propia, como
 * {@code QuoteAdminAccessIT}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecalculationConflictIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String RECALCULATE_PATH = "/quotes/recalculate";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:recalculation-conflict-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private RecalculationLock recalculationLock;

    @Autowired
    private SyncLock syncLock;

    @Autowired
    private ApplicationContext context;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        database = new QuoteDatabase(context);
        database.clean();
        QuoteWeek lastClosed = QuoteWeek.lastClosedAt(Instant.now());
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Match match = database.finishedMatch("560600", lastClosed, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90));
        adminToken = new AuthTestHelper(mvc)
                .login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void conOtroRecalculoEnCursoResponde409YNoCotizaNada() throws Exception {
        MvcTestResult result = recalculationLock.runExclusively(this::recalculate);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(result).get("message").asString()).isEqualTo("Ya hay un recálculo en curso.");
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void conUnaSincronizacionEnCursoResponde409YNoCotizaNada() throws Exception {
        MvcTestResult result = syncLock.runExclusively(this::recalculate);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(result).get("message").asString())
                .isEqualTo("No se puede recalcular: hay una sincronización en curso.");
        assertThat(database.quoteCount()).isZero();
    }

    private MvcTestResult recalculate() {
        return mvc.post().uri(RECALCULATE_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
