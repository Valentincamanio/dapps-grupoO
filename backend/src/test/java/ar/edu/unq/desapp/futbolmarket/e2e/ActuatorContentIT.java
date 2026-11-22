package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que publica {@code /actuator/prometheus} de punta a punta (US1): el formato de exposición, la
 * etiqueta común {@code application}, el histograma HTTP con las rutas como plantilla y las
 * métricas técnicas. Lleva {@link AutoConfigureMetrics} porque sin ella Boot apaga la exportación
 * en los tests y el endpoint responde 404 (research D12). El {@code ADMIN} y esa configuración
 * vienen de {@link ActuatorITSupport}.
 *
 * <p>También cubre {@code /actuator/info} y que ningún endpoint expuesto, para ningún rol, devuelva
 * un secreto (US3), entre ellos el token de Football-Data ficticio de {@link ActuatorITSupport}.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class ActuatorContentIT extends ActuatorITSupport {

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

    /** Cuántas series publica cada métrica propia desde el arranque (data-model §2 de la 012). */
    private static final Map<String, Integer> OWN_SERIES = Map.of(
            "futbolmarket_market_orders_total{", 6,
            "futbolmarket_quotes_recalculation_seconds_count{", 9,
            "futbolmarket_sync_seconds_count{", 12,
            "futbolmarket_auth_logins_total{", 2,
            "futbolmarket_auth_registrations_total{", 1,
            "futbolmarket_users_registered{", 1,
            "futbolmarket_market_orders_open{", 1);
    private static final String ORDERS_METRIC_PATH = "/actuator/metrics/futbolmarket.market.orders";
    private static final String ORDERS_DESCRIPTION = "Órdenes de compra, venta y cancelación pedidas por usuarios, "
            + "aceptadas o rechazadas por el mercado.";

    private static final String INFO_PATH = "/actuator/info";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> BUILD_KEYS = List.of("name", "description", "version", "artifact", "group", "time");
    private static final List<String> MONITORING_PATHS = List.of(
            "/actuator/health", "/actuator/health/db", INFO_PATH, "/actuator/metrics", PROMETHEUS_PATH,
            "/actuator/loggers");

    @Autowired
    private MockMvcTester mvc;

    @Value("${futbolmarket.security.jwt.secret}")
    private String jwtSecret;

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

    @Test
    void recienLevantadoFiguranLasTreintaSeriesPropiasYLosDosGauges() throws Exception {
        List<String> samples = sampleLines();

        assertThat(OWN_SERIES).allSatisfy((prefix, series) ->
                assertThat(samples.stream().filter(line -> line.startsWith(prefix))).hasSize(series));
    }

    @Test
    void elDetalleDeUnaMetricaPropiaTraeSuDescripcionYSusEtiquetas() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_METRIC_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertThat(body.get("description").asString()).isEqualTo(ORDERS_DESCRIPTION);
        assertThat(body.get("availableTags").valueStream().map(tag -> tag.get("tag").asString()))
                .contains("operation", "result");
    }

    @Test
    void elInfoTraeElBuildYJavaSinVariablesDeEntorno() throws Exception {
        TestUser user = new AuthTestHelper(mvc).registerUser();

        MvcTestResult result = mvc.get().uri(INFO_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode body = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        JsonNode build = body.get("build");
        assertThat(BUILD_KEYS).allSatisfy(key -> assertThat(build.hasNonNull(key)).as(key).isTrue());
        assertThat(build.get("name").asString()).isEqualTo("futbol-market");
        assertThat(body.get("java").get("version").asString()).isNotBlank();
        assertThat(body.has("env")).isFalse();
    }

    @Test
    void ningunEndpointDeMonitoreoDevuelveUnSecretoParaNingunRol() throws Exception {
        AuthTestHelper helper = new AuthTestHelper(mvc);
        TestUser user = helper.registerUser();
        String userToken = helper.login(user);
        List<String> secrets = List.of(jwtSecret, user.apiKey(), userToken, adminToken, FAKE_FOOTBALL_DATA_TOKEN);

        for (String path : MONITORING_PATHS) {
            List<String> bodies = List.of(
                    mvc.get().uri(path).exchange().getResponse().getContentAsString(),
                    mvc.get().uri(path).header(API_KEY_HEADER, user.apiKey()).exchange().getResponse().getContentAsString(),
                    mvc.get().uri(path).header(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken).exchange()
                            .getResponse().getContentAsString(),
                    mvc.get().uri(path).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange()
                            .getResponse().getContentAsString());
            assertThat(bodies).as(path).allSatisfy(body ->
                    assertThat(secrets).allSatisfy(secret -> assertThat(body).doesNotContain(secret)));
        }
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
