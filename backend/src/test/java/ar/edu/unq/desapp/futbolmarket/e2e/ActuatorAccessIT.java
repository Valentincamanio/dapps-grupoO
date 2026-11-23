package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La matriz de acceso de contracts/actuator-access.md de la 012 (US3): cada endpoint de Actuator
 * pedido como visitante, {@code USER} y {@code ADMIN}, con token de sesión y con clave de API. El
 * {@code ADMIN} y {@code @AutoConfigureMetrics} (para que {@code /actuator/prometheus} exista)
 * vienen de {@link ActuatorITSupport}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorAccessIT extends ActuatorITSupport {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PATH = "/auth/me/api-key";

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String DB_HEALTH_PATH = "/actuator/health/db";
    private static final String ACTUATOR_ROOT = "/actuator";
    private static final String HTTP_METRIC_PATH = "/actuator/metrics/http.server.requests";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String UNKNOWN_API_KEY = "claveInventadaQueNoCorrespondeANingunUsuario";

    private static final Set<String> EXPOSED_ENDPOINTS = Set.of("health", "info", "metrics", "prometheus", "loggers");
    private static final List<String> NOT_EXPOSED = List.of(
            "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
            "/actuator/threaddump", "/actuator/mappings", "/actuator/shutdown");
    private static final List<String> LEAKED_FRAGMENTS = List.of("spring.", "datasource", "password", "jwt");

    private static final int OK = HttpStatus.OK.value();
    private static final int UNAUTHORIZED = HttpStatus.UNAUTHORIZED.value();
    private static final int FORBIDDEN = HttpStatus.FORBIDDEN.value();
    private static final int NOT_FOUND = HttpStatus.NOT_FOUND.value();

    /** Una fila de la matriz: el path y el status esperado para visitante, {@code USER} y {@code ADMIN}. */
    private record Row(String path, int visitor, int user, int admin) {
    }

    private static final List<Row> MATRIX = buildMatrix();

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String userToken;
    private String userApiKey;
    private String adminToken;
    private String adminApiKey;

    enum Caller { USER, ADMIN }

    enum Credential { BEARER, API_KEY }

    @BeforeAll
    void setUpCallers() throws Exception {
        AuthTestHelper helper = new AuthTestHelper(mvc);
        TestUser user = helper.registerUser();
        userToken = helper.login(user);
        userApiKey = user.apiKey();
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        MvcTestResult issued = mvc.post().uri(API_KEY_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
        adminApiKey = jsonMapper.readTree(issued.getResponse().getContentAsString()).get("apiKey").asString();
    }

    // --- matriz de status ---

    @ParameterizedTest(name = "{0} como visitante → {1}")
    @MethodSource("visitorCases")
    void elVisitanteRecibeElStatusDeLaMatriz(String path, int expected) {
        assertThat(mvc.get().uri(path).exchange().getResponse().getStatus()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} como {1} con {2} → {3}")
    @MethodSource("authenticatedCases")
    void cadaRolRecibeElStatusDeLaMatrizConCualquierCredencial(String path, Caller caller, Credential credential,
                                                                int expected) {
        assertThat(get(path, caller, credential).getResponse().getStatus()).isEqualTo(expected);
    }

    static Stream<Arguments> visitorCases() {
        return MATRIX.stream().map(row -> Arguments.of(row.path(), row.visitor()));
    }

    static Stream<Arguments> authenticatedCases() {
        List<Arguments> cases = new ArrayList<>();
        for (Row row : MATRIX) {
            for (Credential credential : Credential.values()) {
                cases.add(Arguments.of(row.path(), Caller.USER, credential, row.user()));
                cases.add(Arguments.of(row.path(), Caller.ADMIN, credential, row.admin()));
            }
        }
        return cases.stream();
    }

    // --- salud ---

    @Test
    void laRaizDeSaludLeDaAlVisitanteSoloElEstado() throws Exception {
        JsonNode body = json(mvc.get().uri(HEALTH_PATH).exchange());

        assertThat(body.propertyNames()).containsExactly("status");
        assertThat(body.get("status").asString()).isEqualTo("UP");
    }

    @ParameterizedTest
    @MethodSource("everyCallerWithCredential")
    void laRaizDeSaludLeDaSoloElEstadoTambienAQuienTraeCredencial(Caller caller, Credential credential) throws Exception {
        JsonNode body = json(get(HEALTH_PATH, caller, credential));

        assertThat(body.propertyNames()).containsExactly("status");
        assertThat(body.get("status").asString()).isEqualTo("UP");
    }

    @ParameterizedTest
    @EnumSource(Credential.class)
    void elUsuarioVeElEstadoDeUnComponenteSinDetalles(Credential credential) throws Exception {
        JsonNode body = json(get(DB_HEALTH_PATH, Caller.USER, credential));

        assertThat(body.get("status").asString()).isEqualTo("UP");
        assertThat(body.has("details")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Credential.class)
    void elAdministradorVeElEstadoDeUnComponenteConDetallesSinLaUrlDeLaBase(Credential credential) throws Exception {
        MvcTestResult result = get(DB_HEALTH_PATH, Caller.ADMIN, credential);
        JsonNode body = json(result);

        assertThat(body.get("status").asString()).isEqualTo("UP");
        assertThat(body.get("details").get("database").asString()).isEqualTo("H2");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("jdbc:");
    }

    // --- raíz y métricas ---

    @Test
    void laRaizDelActuatorLeListaAlAdministradorSoloLosCincoExpuestos() throws Exception {
        JsonNode links = json(get(ACTUATOR_ROOT, Caller.ADMIN, Credential.BEARER)).get("_links");

        Set<String> endpoints = links.propertyNames().stream()
                .filter(name -> !"self".equals(name))
                .map(name -> name.split("-")[0])
                .collect(Collectors.toSet());
        assertThat(endpoints).isEqualTo(EXPOSED_ENDPOINTS);
    }

    @Test
    void elDetalleDeLaMetricaHttpTraeMedicionesYEtiquetas() throws Exception {
        JsonNode body = json(get(HTTP_METRIC_PATH, Caller.ADMIN, Credential.API_KEY));

        assertThat(body.get("measurements").isEmpty()).isFalse();
        assertThat(body.get("availableTags").valueStream().map(tag -> tag.get("tag").asString()))
                .contains("uri", "status", "method");
    }

    // --- no expuestos ---

    @ParameterizedTest
    @MethodSource("notExposedWithEveryCaller")
    void unEndpointNoExpuestoNoDevuelveConfiguracion(String path, Caller caller) throws Exception {
        String body = get(path, caller, Credential.BEARER).getResponse().getContentAsString().toLowerCase();

        assertThat(LEAKED_FRAGMENTS).allSatisfy(fragment -> assertThat(body).doesNotContain(fragment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/heapdump"})
    void unEndpointNoExpuestoNoLeDevuelveConfiguracionAlVisitante(String path) throws Exception {
        String body = mvc.get().uri(path).exchange().getResponse().getContentAsString().toLowerCase();

        assertThat(LEAKED_FRAGMENTS).allSatisfy(fragment -> assertThat(body).doesNotContain(fragment));
    }

    // --- cuerpos de error ---

    @Test
    void elVisitanteRecibeUn401ConElFormatoComun() throws Exception {
        MvcTestResult result = mvc.get().uri(ACTUATOR_ROOT + "/prometheus").exchange();

        assertApiError(result, UNAUTHORIZED, MISSING_CREDENTIAL_MESSAGE, "/actuator/prometheus");
    }

    @Test
    void elUsuarioRecibeUn403ConElFormatoComun() throws Exception {
        MvcTestResult result = get("/actuator/metrics", Caller.USER, Credential.API_KEY);

        assertApiError(result, FORBIDDEN, FORBIDDEN_MESSAGE, "/actuator/metrics");
    }

    @Test
    void unaCredencialInvalidaRecibe401EnUnEndpointProtegido() {
        MvcTestResult result = mvc.get().uri("/actuator/info").header(API_KEY_HEADER, UNKNOWN_API_KEY).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(UNAUTHORIZED);
    }

    // --- Swagger ---

    @Test
    void elGrupoDeMonitoreoListaElActuatorConRequisitosDeSeguridad() throws Exception {
        JsonNode paths = json(mvc.get().uri("/v3/api-docs/monitoring").exchange()).get("paths");

        assertThat(paths.propertyNames()).isNotEmpty().allMatch(path -> path.startsWith(ACTUATOR_ROOT));
        assertThat(paths.propertyNames()).contains("/actuator/prometheus", "/actuator/metrics", HEALTH_PATH);
        assertThat(securityOf(paths, "/actuator/prometheus")).contains("bearerAuth", "apiKeyAuth");
        assertThat(securityOf(paths, HEALTH_PATH)).isEmpty();
    }

    @Test
    void elGrupoDeNegocioNoTieneNingunEndpointDelActuator() throws Exception {
        JsonNode paths = json(mvc.get().uri("/v3/api-docs/business").exchange()).get("paths");

        assertThat(paths.propertyNames()).isNotEmpty().noneMatch(path -> path.startsWith(ACTUATOR_ROOT));
    }

    @Test
    void laEspecificacionPorDefectoSigueRespondiendoConLosDosEsquemas() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(OK);
        assertThat(json(result).get("components").get("securitySchemes").propertyNames())
                .contains("bearerAuth", "apiKeyAuth");
    }

    // --- fuentes ---

    static Stream<Arguments> everyCallerWithCredential() {
        return Stream.of(Caller.values()).flatMap(caller ->
                Stream.of(Credential.values()).map(credential -> Arguments.of(caller, credential)));
    }

    static Stream<Arguments> notExposedWithEveryCaller() {
        return NOT_EXPOSED.stream().flatMap(path ->
                Stream.of(Caller.values()).map(caller -> Arguments.of(path, caller)));
    }

    private static List<Row> buildMatrix() {
        List<Row> rows = new ArrayList<>(List.of(
                new Row(HEALTH_PATH, OK, OK, OK),
                new Row(DB_HEALTH_PATH, UNAUTHORIZED, OK, OK),
                new Row("/actuator/health/inexistente", UNAUTHORIZED, NOT_FOUND, NOT_FOUND),
                new Row("/actuator/info", UNAUTHORIZED, OK, OK),
                new Row(ACTUATOR_ROOT, UNAUTHORIZED, FORBIDDEN, OK),
                new Row("/actuator/metrics", UNAUTHORIZED, FORBIDDEN, OK),
                new Row(HTTP_METRIC_PATH, UNAUTHORIZED, FORBIDDEN, OK),
                new Row("/actuator/prometheus", UNAUTHORIZED, FORBIDDEN, OK),
                new Row("/actuator/loggers", UNAUTHORIZED, FORBIDDEN, OK),
                new Row("/actuator/loggers/ar.edu.unq.desapp.futbolmarket", UNAUTHORIZED, FORBIDDEN, OK)));
        NOT_EXPOSED.forEach(path -> rows.add(new Row(path, UNAUTHORIZED, NOT_FOUND, NOT_FOUND)));
        return List.copyOf(rows);
    }

    // --- apoyo ---

    private MvcTestResult get(String path, Caller caller, Credential credential) {
        return switch (credential) {
            case BEARER -> mvc.get().uri(path).header(AUTHORIZATION_HEADER, BEARER_PREFIX + tokenOf(caller)).exchange();
            case API_KEY -> mvc.get().uri(path).header(API_KEY_HEADER, apiKeyOf(caller)).exchange();
        };
    }

    private String tokenOf(Caller caller) {
        return caller == Caller.ADMIN ? adminToken : userToken;
    }

    private String apiKeyOf(Caller caller) {
        return caller == Caller.ADMIN ? adminApiKey : userApiKey;
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Los esquemas que pide el {@code GET} de un path del grupo; vacío si no pide ninguno. */
    private static List<String> securityOf(JsonNode paths, String path) {
        JsonNode security = paths.get(path).get("get").get("security");
        if (security == null) {
            return List.of();
        }
        return security.valueStream().flatMap(requirement -> requirement.propertyNames().stream()).toList();
    }

    private void assertApiError(MvcTestResult result, int status, String message, String path) throws Exception {
        JsonNode body = json(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(message);
        assertThat(body.get("path").asString()).isEqualTo(path);
    }
}
