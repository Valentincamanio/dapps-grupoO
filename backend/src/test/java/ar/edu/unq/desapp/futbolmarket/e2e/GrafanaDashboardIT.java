package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El dashboard de Grafana versionado contra lo que publica el backend (research R17 de la 013 y
 * contracts/grafana-dashboard.md §6): el JSON parsea, cada métrica que consulta existe en
 * {@code /actuator/prometheus}, todo panel con consultas apunta al data source provisionado y el
 * ejemplo de la credencial no trae contraseña.
 *
 * <p>Es un test de archivos: no necesita Grafana ni Prometheus. Las rutas son relativas a
 * {@code backend/}, el directorio de trabajo de los tests de Gradle. El archivo local
 * {@code admin-credentials} nunca se lee, aunque exista (SC-007).</p>
 */
class GrafanaDashboardIT extends ActuatorITSupport {

    private static final Path GRAFANA = Path.of("..", "monitoring", "grafana");
    private static final Path DASHBOARD = GRAFANA.resolve("dashboards").resolve("futbol-market.json");
    private static final Path DATASOURCE = GRAFANA.resolve("provisioning").resolve("datasources")
            .resolve("prometheus.yaml");
    private static final Path CREDENTIALS_EXAMPLE = GRAFANA.resolve("admin-credentials.example");

    private static final Pattern METRIC_NAME =
            Pattern.compile("\\b(futbolmarket_[a-z_]+|http_server_requests_[a-z_]+|cache_[a-z_]+)\\b");
    private static final Pattern DATASOURCE_UID = Pattern.compile("(?m)^\\s*uid:\\s*([\\w-]+)");
    private static final String PASSWORD_KEY = "admin_password=";

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String RANKING_PATH = "/players/ranking";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = new AuthTestHelper(mvc).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void elDashboardParseaYTieneSuUidYSuVersionDeEsquema() throws IOException {
        JsonNode dashboard = dashboard();

        assertThat(dashboard.get("uid").asString()).isEqualTo("futbol-market");
        assertThat(dashboard.get("title").asString()).isEqualTo("futbol-market");
        assertThat(dashboard.get("schemaVersion").asInt()).isEqualTo(39);
        assertThat(panels(dashboard)).isNotEmpty();
    }

    @Test
    void cadaMetricaQueConsultaElDashboardExisteEnElEndpointDePrometheus() throws Exception {
        Set<String> names = metricNames(dashboard());
        assertThat(mvc.get().uri(RANKING_PATH).exchange().getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());

        List<String> samples = sampleLines();

        assertThat(names).isNotEmpty().allSatisfy(name ->
                assertThat(samples).as(name).anyMatch(line -> line.startsWith(name + "{") || line.startsWith(name + " ")));
    }

    @Test
    void todoPanelConConsultasApuntaAlDataSourceProvisionado() throws IOException {
        Matcher uid = DATASOURCE_UID.matcher(Files.readString(DATASOURCE));
        assertThat(uid.find()).isTrue();
        String datasource = uid.group(1);

        List<JsonNode> withTargets = panels(dashboard()).stream().filter(panel -> panel.has("targets")).toList();

        assertThat(withTargets).isNotEmpty().allSatisfy(panel -> {
            String title = panel.get("title").asString();
            assertThat(panel.path("datasource").path("uid").asString()).as(title).isEqualTo(datasource);
            assertThat(panel.get("targets").valueStream()).as(title).isNotEmpty().allSatisfy(target ->
                    assertThat(target.path("datasource").path("uid").asString()).as(title).isEqualTo(datasource));
        });
    }

    @Test
    void elEjemploDeLaCredencialNoTraeContrasena() throws IOException {
        List<String> passwordLines = Files.readAllLines(CREDENTIALS_EXAMPLE).stream()
                .map(String::trim)
                .filter(line -> line.startsWith(PASSWORD_KEY))
                .toList();

        assertThat(passwordLines).containsExactly(PASSWORD_KEY);
    }

    // --- apoyo ---

    private JsonNode dashboard() throws IOException {
        return jsonMapper.readTree(Files.readString(DASHBOARD));
    }

    /** Todos los paneles, también los anidados en una fila plegada. */
    private static List<JsonNode> panels(JsonNode dashboard) {
        List<JsonNode> panels = new ArrayList<>();
        dashboard.path("panels").valueStream().forEach(panel -> {
            panels.add(panel);
            panel.path("panels").valueStream().forEach(panels::add);
        });
        return panels;
    }

    private static Set<String> metricNames(JsonNode dashboard) {
        Set<String> names = new TreeSet<>();
        panels(dashboard).forEach(panel -> panel.path("targets").valueStream().forEach(target -> {
            Matcher matcher = METRIC_NAME.matcher(target.path("expr").asString());
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }));
        return names;
    }

    private List<String> sampleLines() throws Exception {
        return mvc.get().uri(PROMETHEUS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange()
                .getResponse().getContentAsString().lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }
}
