package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los cinco endpoints de {@code /strategies/**} son solo para administradores, también los de
 * lectura (US4.12, US5.13, SC-011): sin credencial responden 401 y con la de un usuario común 403,
 * y en ningún caso cambia el catálogo.
 *
 * <p>Los pedidos son válidos (activar la B, editar la B, crear una nueva): si llegaran al controller,
 * cambiarían algo. Usa una H2 propia, con las dos estrategias de arranque.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrategyAccessIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final String WEIGHTS = """
            {"defaultWeights": {"GOALS": 0.50, "RATING": 0.50}}""";
    private static final String NEW_STRATEGY = """
            {"name": "Intrusa", "defaultWeights": {"GOALS": 0.50, "RATING": 0.50}}""";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:strategy-access-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StrategySQLDAO strategyDAO;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private long balancedId;
    private List<CatalogRow> catalogBefore;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
        balancedId = strategyDAO.findAll().stream()
                .filter(row -> row.getName().equals(StartingStrategies.BALANCED_BY_POSITION_NAME))
                .mapToLong(StrategySQL::getId)
                .min()
                .orElseThrow();
        catalogBefore = catalog();
    }

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.POST, "/strategies", NEW_STRATEGY),
                new Endpoint(HttpMethod.GET, "/strategies", null),
                new Endpoint(HttpMethod.GET, "/strategies/%d", null),
                new Endpoint(HttpMethod.POST, "/strategies/%d/versions", WEIGHTS),
                new Endpoint(HttpMethod.POST, "/strategies/%d/activate", null));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void sinCredencialResponde401YElCatalogoQuedaIgual(Endpoint endpoint) throws Exception {
        MvcTestResult result = request(endpoint).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(catalog()).isEqualTo(catalogBefore);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void unUsuarioComunConSuTokenRecibe403YElCatalogoQuedaIgual(Endpoint endpoint) throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = request(endpoint)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + helper.login(user))
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(catalog()).isEqualTo(catalogBefore);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void unUsuarioComunConSuClaveDeApiRecibe403YElCatalogoQuedaIgual(Endpoint endpoint) throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = request(endpoint).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(catalog()).isEqualTo(catalogBefore);
    }

    private MockMvcRequestBuilder request(Endpoint endpoint) {
        MockMvcRequestBuilder request = mvc.method(endpoint.method()).uri(endpoint.path().formatted(balancedId));
        if (endpoint.body() != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return request;
    }

    private List<CatalogRow> catalog() {
        return strategyDAO.findAll().stream()
                .map(row -> new CatalogRow(row.getId(), row.getName(), row.getVersionNumber(), row.isActive()))
                .sorted((first, second) -> Long.compare(first.id(), second.id()))
                .toList();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    record Endpoint(HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return method + " " + path.replace("%d", "{id}");
        }
    }

    private record CatalogRow(Long id, String name, int version, boolean active) {
    }
}
