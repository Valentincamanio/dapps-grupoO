package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los componentes {@code footballData} y {@code sync} de {@code /actuator/health} de punta a punta
 * (US4 de la 012; contracts/health-indicators.md). El perfil {@code test} no tiene token de
 * Football-Data ni sincronizaciones; la clase anidada agrega un token ficticio.
 *
 * <p>Usa una H2 propia: la clase anidada levanta un segundo contexto sobre la misma base, y como
 * corre después de los casos de afuera, no pisa los datos de ningún otro test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthIndicatorsIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String FAKE_TOKEN = "tokenFicticioDeSaludDeFootballData0123456789";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String FOOTBALL_DATA_PATH = "/actuator/health/footballData";
    private static final String SYNC_PATH = "/actuator/health/sync";
    private static final String UP = "UP";
    private static final String UNKNOWN = "UNKNOWN";
    private static final Duration RECENT = Duration.ofDays(1);
    private static final Duration OLD = Duration.ofDays(30);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private LastFullSyncRepository lastFullSyncRepository;

    @Autowired
    private LastFullSyncSQLDAO lastFullSyncDAO;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:health-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = login(mvc);
    }

    @AfterEach
    void forgetSyncs() {
        lastFullSyncDAO.deleteAll();
    }

    // --- sin token y sin sincronizaciones ---

    @Test
    void elAgregadoSigueEnUpParaElVisitanteAunqueLosPropiosEstenEnUnknown() throws Exception {
        MvcTestResult result = mvc.get().uri(HEALTH_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = json(result);
        assertThat(body.propertyNames()).containsExactly("status");
        assertThat(body.get("status").asString()).isEqualTo(UP);
    }

    /**
     * La raíz muestra solo el estado también al {@code ADMIN} (FR-031); los componentes se piden
     * por su propio path.
     */
    @Test
    void elAgregadoSigueEnUpParaElAdminConLosDosComponentesEnUnknown() throws Exception {
        JsonNode root = json(asAdmin(HEALTH_PATH));
        JsonNode footballData = json(asAdmin(FOOTBALL_DATA_PATH));
        JsonNode sync = json(asAdmin(SYNC_PATH));

        assertThat(root.get("status").asString()).isEqualTo(UP);
        assertThat(footballData.get("status").asString()).isEqualTo(UNKNOWN);
        assertThat(sync.get("status").asString()).isEqualTo(UNKNOWN);
    }

    @Test
    void sinTokenFootballDataEsUnknownConTokenConfiguredFalse() throws Exception {
        MvcTestResult result = asAdmin(FOOTBALL_DATA_PATH);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = json(result);
        assertThat(body.get("status").asString()).isEqualTo(UNKNOWN);
        assertThat(body.at("/details/tokenConfigured").asBoolean()).isFalse();
    }

    @Test
    void sinSincronizacionesSyncEsUnknownConLasDosFuentesSinSincronizar() throws Exception {
        MvcTestResult result = asAdmin(SYNC_PATH);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = json(result);
        assertThat(body.get("status").asString()).isEqualTo(UNKNOWN);
        assertThat(body.at("/details/catchUpAfter").asString()).isEqualTo("PT168H");
        assertThat(body.at("/details/football_data").propertyNames()).containsExactly("synchronized");
        assertThat(body.at("/details/football_data/synchronized").asBoolean()).isFalse();
        assertThat(body.at("/details/whoscored/synchronized").asBoolean()).isFalse();
    }

    // --- con sincronizaciones ---

    @Test
    void conLasDosRecientesSyncEsUp() throws Exception {
        Instant footballDataEnd = savedAgo(SyncSource.FOOTBALL_DATA, RECENT);
        savedAgo(SyncSource.WHOSCORED, RECENT);

        JsonNode body = json(asAdmin(SYNC_PATH));

        assertThat(body.get("status").asString()).isEqualTo(UP);
        assertThat(body.at("/details/football_data/synchronized").asBoolean()).isTrue();
        assertThat(body.at("/details/football_data/stale").asBoolean()).isFalse();
        assertThat(Instant.parse(body.at("/details/football_data/lastSuccessfulFullSync").asString()))
                .isEqualTo(footballDataEnd);
        assertThat(body.at("/details/whoscored/stale").asBoolean()).isFalse();
    }

    @Test
    void conUnaVencidaSyncEsUnknownConStaleTrueYResponde200() throws Exception {
        savedAgo(SyncSource.FOOTBALL_DATA, OLD);
        savedAgo(SyncSource.WHOSCORED, RECENT);

        MvcTestResult result = asAdmin(SYNC_PATH);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = json(result);
        assertThat(body.get("status").asString()).isEqualTo(UNKNOWN);
        assertThat(body.at("/details/football_data/stale").asBoolean()).isTrue();
        assertThat(body.at("/details/whoscored/stale").asBoolean()).isFalse();
    }

    // --- con token ---

    /**
     * Con un token ficticio: segundo contexto, sobre la misma H2 de la clase.
     */
    @Nested
    @TestPropertySource(properties = "futbolmarket.football-data.token=" + FAKE_TOKEN)
    class ConToken {

        /** Del contexto con token: el de la clase de afuera es el del contexto sin token. */
        @Autowired
        private MockMvcTester tokenMvc;

        private String tokenAdminToken;

        @BeforeEach
        void loginAsAdminWithToken() throws Exception {
            tokenAdminToken = login(tokenMvc);
        }

        @Test
        void footballDataEsUpConTokenConfiguredTrueYSinElToken() throws Exception {
            MvcTestResult result = asAdmin(tokenMvc, tokenAdminToken, FOOTBALL_DATA_PATH);

            assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
            JsonNode body = json(result);
            assertThat(body.get("status").asString()).isEqualTo(UP);
            assertThat(body.at("/details/tokenConfigured").asBoolean()).isTrue();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(FAKE_TOKEN);
        }

        @Test
        void elAgregadoCompletoNoContieneElToken() throws Exception {
            MvcTestResult result = asAdmin(tokenMvc, tokenAdminToken, HEALTH_PATH);

            assertThat(result.getResponse().getContentAsString()).doesNotContain(FAKE_TOKEN);
        }
    }

    private Instant savedAgo(SyncSource source, Duration age) {
        return lastFullSyncRepository.save(new LastFullSync(source, Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(age))).finishedAt();
    }

    private MvcTestResult asAdmin(String path) {
        return asAdmin(mvc, adminToken, path);
    }

    private static MvcTestResult asAdmin(MockMvcTester tester, String token, String path) {
        return tester.get().uri(path).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
    }

    private static String login(MockMvcTester tester) throws Exception {
        return new AuthTestHelper(tester).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
