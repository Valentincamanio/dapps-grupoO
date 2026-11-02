package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategySeeder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La baja lógica de una estrategia de punta a punta ({@code DELETE /strategies/{id}}): deja de
 * listarse y de poder usarse, se conserva en la base, su nombre queda tomado y la semilla no la
 * vuelve a crear. La activa no se elimina.
 *
 * <p>Usa una H2 propia. Antes de cada test vuelve a las dos estrategias de arranque, con la A
 * activa.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrategyDeletionIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String STRATEGIES_PATH = "/strategies";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MATCH_PERFORMANCE = StartingStrategies.MATCH_PERFORMANCE_NAME;
    private static final String BALANCED = StartingStrategies.BALANCED_BY_POSITION_NAME;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:strategy-deletion-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StrategySQLDAO strategyDAO;

    @Autowired
    private StrategySeeder seeder;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        resetStrategies();
        helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @AfterEach
    void cleanDatabase() {
        resetStrategies();
    }

    @Test
    void eliminarUnaInactivaLaSacaDelListadoYLaConservaEnLaBase() throws Exception {
        long balancedId = idOf(BALANCED);

        MvcTestResult result = delete(balancedId, adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(body(get(STRATEGIES_PATH)).values()).extracting(strategy -> strategy.get("name").asString())
                .containsExactly(MATCH_PERFORMANCE);
        assertThat(strategyDAO.findById(balancedId)).get().extracting(StrategySQL::isDeleted).isEqualTo(true);
    }

    @Test
    void unaEliminadaNoSeConsultaNiSeActivaNiSeSimula() throws Exception {
        long balancedId = idOf(BALANCED);
        delete(balancedId, adminToken);

        MvcTestResult consulted = get(STRATEGIES_PATH + "/" + balancedId);
        MvcTestResult activated = post(STRATEGIES_PATH + "/" + balancedId + "/activate", null);
        MvcTestResult simulated = post("/quotes/simulate", "{\"strategyId\": " + balancedId + "}");

        assertThat(consulted.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(activated.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(simulated.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void laEliminadaQuedaRenombradaConLaFechaYGuardaSuNombreOriginal() throws Exception {
        long balancedId = idOf(BALANCED);

        delete(balancedId, adminToken);

        StrategySQL row = strategyDAO.findById(balancedId).orElseThrow();
        assertThat(row.getName()).matches(java.util.regex.Pattern.quote(BALANCED)
                + " \\(eliminada el \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}\\)");
        assertThat(row.getOriginalName()).isEqualTo(BALANCED);
    }

    @Test
    void laSemillaNoVuelveACrearUnaDeArranqueEliminada() throws Exception {
        delete(idOf(BALANCED), adminToken);
        long rowsBefore = strategyDAO.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(strategyDAO.count()).isEqualTo(rowsBefore);
        assertThat(body(get(STRATEGIES_PATH)).values()).hasSize(1);
    }

    @Test
    void elNombreDeUnaEliminadaSePuedeVolverAUsar() throws Exception {
        delete(idOf(BALANCED), adminToken);

        MvcTestResult created = post(STRATEGIES_PATH,
                "{\"name\": \"" + BALANCED + "\", \"defaultWeights\": {\"GOALS\": 0.50, \"RATING\": 0.50}}");

        assertThat(created.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(body(get(STRATEGIES_PATH)).values()).extracting(strategy -> strategy.get("name").asString())
                .containsExactly(MATCH_PERFORMANCE, BALANCED);
        assertThat(strategyDAO.findAll()).filteredOn(StrategySQL::isDeleted).singleElement()
                .satisfies(deleted -> assertThat(deleted.getName()).startsWith(BALANCED + " (eliminada el "));
    }

    @Test
    void laActivaNoSeEliminaYQuedaIgual() throws Exception {
        long activeId = idOf(MATCH_PERFORMANCE);

        MvcTestResult result = delete(activeId, adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(result).get("message").asString()).isEqualTo("No se puede eliminar '" + MATCH_PERFORMANCE
                + "' porque es la estrategia activa: primero hay que activar otra.");
        assertThat(strategyDAO.findAll()).noneMatch(StrategySQL::isDeleted);
    }

    @Test
    void eliminarDosVecesOUnaInexistenteRespondeNotFound() throws Exception {
        long balancedId = idOf(BALANCED);
        delete(balancedId, adminToken);

        MvcTestResult again = delete(balancedId, adminToken);
        MvcTestResult missing = delete(999_999L, adminToken);

        assertThat(again.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void sinCredencialOConUnUsuarioComunNoSeEliminaNada() throws Exception {
        long balancedId = idOf(BALANCED);
        String userToken = helper.login(helper.registerUser());

        MvcTestResult anonymous = mvc.delete().uri(STRATEGIES_PATH + "/" + balancedId).exchange();
        MvcTestResult common = delete(balancedId, userToken);

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(common.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(strategyDAO.findAll()).noneMatch(StrategySQL::isDeleted);
    }

    private long idOf(String name) throws Exception {
        for (JsonNode strategy : body(get(STRATEGIES_PATH)).values()) {
            if (strategy.get("name").asString().equals(name)) {
                return strategy.get("id").asLong();
            }
        }
        throw new AssertionError("No está la estrategia " + name);
    }

    /**
     * Vuelve a las dos estrategias de arranque (las versiones se borran de la última a la primera por
     * la clave a la raíz).
     */
    private void resetStrategies() {
        strategyDAO.deleteAll(strategyDAO.findAll().stream()
                .sorted(Comparator.comparingInt(StrategySQL::getVersionNumber).reversed())
                .toList());
        seeder.run(new DefaultApplicationArguments());
    }

    private MvcTestResult delete(long id, String token) {
        return mvc.delete().uri(STRATEGIES_PATH + "/" + id).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
    }

    private MvcTestResult post(String uri, String requestBody) {
        var request = mvc.post().uri(uri).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken);
        if (requestBody != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(requestBody);
        }
        return request.exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
