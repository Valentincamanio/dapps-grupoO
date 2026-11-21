package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;

/**
 * Lo que publica {@code /actuator/prometheus} de punta a punta (US1): el formato de exposición, la
 * etiqueta común {@code application}, el histograma HTTP con las rutas como plantilla y las
 * métricas técnicas. Lleva {@link AutoConfigureMetrics} porque sin ella Boot apaga la exportación
 * en los tests y el endpoint responde 404 (research D12). El {@code ADMIN} se da de alta con una H2
 * propia, igual que {@code AdminAccountIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ActuatorContentIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String PLAYERS_PATH = "/players/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String COMMENT_PREFIX = "#";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String VERSION_PARAMETER = "version=";

    private static final String APPLICATION_LABEL = "application=\"futbol-market\"";
    private static final String HTTP_BUCKET = "http_server_requests_seconds_bucket{";
    private static final String HTTP_SERIES = "http_server_requests_seconds_";
    private static final String PLAYER_TEMPLATE_URI = "uri=\"/players/{id}\"";
    private static final String BUCKET_BOUND = "le=";
    private static final String NOT_FOUND_STATUS = "status=\"404\"";
    private static final String UNKNOWN_ROUTE = "/players/7/foo/bar";
    private static final String AUDIT_MARK = " | user=";
    private static final String ACTUATOR_PREFIX = "/actuator";

    private static final long FIRST_PLAYER = 7L;
    private static final long SECOND_PLAYER = 12L;
    private static final long FIRST_MISSING_PLAYER = 950_001L;
    private static final int DISTINCT_PLAYERS = 50;

    private static final List<String> TECHNICAL_METRICS = List.of(
            "jvm_memory_used_bytes",
            "jvm_threads_live_threads",
            "process_cpu_usage",
            "process_uptime_seconds",
            "hikaricp_connections_active");

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:actuator-content-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void elAdministradorRecibeLasMetricasEnElFormatoDeExposicionDePrometheus() {
        MvcTestResult result = scrape();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getResponse().getContentType()).startsWith(TEXT_PLAIN).contains(VERSION_PARAMETER);
    }

    @Test
    void todaLineaDeMuestraLlevaLaEtiquetaDeLaAplicacion() throws Exception {
        List<String> samples = sampleLines();

        assertThat(samples).isNotEmpty().allMatch(line -> line.contains(APPLICATION_LABEL));
    }

    @Test
    void lasRutasConIdSeAgrupanEnSuPlantillaConHistograma() throws Exception {
        requestPlayer(FIRST_PLAYER);
        requestPlayer(SECOND_PLAYER);

        List<String> samples = sampleLines();

        assertThat(samples).anyMatch(line -> line.startsWith(HTTP_BUCKET)
                && line.contains(PLAYER_TEMPLATE_URI) && line.contains(BUCKET_BOUND));
        assertThat(samples).noneMatch(line -> line.contains(concreteUri(FIRST_PLAYER))
                || line.contains(concreteUri(SECOND_PLAYER)));
    }

    @Test
    void cincuentaIdsDistintosNoAgreganSeriesALaPlantilla() throws Exception {
        requestPlayer(FIRST_MISSING_PLAYER);
        long seriesWithOneId = templateSeriesCount();

        LongStream.range(1, DISTINCT_PLAYERS + 1L).forEach(offset -> requestPlayer(FIRST_MISSING_PLAYER + offset));

        assertThat(templateSeriesCount()).isEqualTo(seriesWithOneId);
    }

    @Test
    void unaRutaInexistenteCuentaConUnaUriFijaYNoConLaRutaConcreta() throws Exception {
        MvcTestResult missing = mvc.get().uri(UNKNOWN_ROUTE)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();

        List<String> samples = sampleLines();

        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(samples).anyMatch(line -> line.startsWith(HTTP_SERIES) && line.contains(NOT_FOUND_STATUS));
        assertThat(samples).noneMatch(line -> line.contains("uri=\"" + UNKNOWN_ROUTE + "\""));
    }

    @Test
    void figuranLasMetricasTecnicasDeLaJvmElProcesoYElPool() throws Exception {
        List<String> samples = sampleLines();

        assertThat(TECHNICAL_METRICS).allSatisfy(metric ->
                assertThat(samples).anyMatch(line -> line.startsWith(metric + "{")));
    }

    @Test
    void losRequestsAlActuatorNoDejanLineaDeAuditoria(CapturedOutput output) throws Exception {
        sampleLines();
        mvc.get().uri("/actuator/health").exchange();
        mvc.get().uri("/actuator/info").header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();

        assertThat(output.getOut().lines().filter(line -> line.contains(AUDIT_MARK)))
                .noneMatch(line -> line.contains(ACTUATOR_PREFIX));
    }

    // --- apoyo ---

    private MvcTestResult scrape() {
        return mvc.get().uri(PROMETHEUS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
    }

    /** Las líneas de muestra de {@code /actuator/prometheus} pedido como {@code ADMIN} (sin comentarios). */
    private List<String> sampleLines() throws Exception {
        return scrape().getResponse().getContentAsString().lines()
                .filter(line -> !line.isBlank() && !line.startsWith(COMMENT_PREFIX))
                .toList();
    }

    /** Cuántas series del histograma HTTP hay con la plantilla de la ruta de un jugador. */
    private long templateSeriesCount() throws Exception {
        return sampleLines().stream()
                .filter(line -> line.startsWith(HTTP_SERIES) && line.contains(PLAYER_TEMPLATE_URI))
                .count();
    }

    private void requestPlayer(long id) {
        mvc.get().uri(PLAYERS_PATH + id).exchange();
    }

    private static String concreteUri(long id) {
        return "uri=\"" + PLAYERS_PATH + id + "\"";
    }
}
