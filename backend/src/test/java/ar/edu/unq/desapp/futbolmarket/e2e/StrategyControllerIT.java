package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategySeeder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La administración de estrategias de punta a punta, como administrador (US4): las de arranque,
 * crear, editar, activar, listar y consultar, los rechazos y que crear o editar no toca las
 * cotizaciones guardadas.
 *
 * <p>Usa una H2 propia. Antes de cada test borra las cotizaciones y las estrategias y vuelve a
 * correr la semilla, así cada uno arranca con A activa en su versión 1 y B en su versión 1. Los ids
 * cambian entre tests, por eso se leen del listado.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrategyControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String STRATEGIES_PATH = "/strategies";
    private static final String RECALCULATE_PATH = "/quotes/recalculate";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MATCH_PERFORMANCE = StartingStrategies.MATCH_PERFORMANCE_NAME;
    private static final String BALANCED = StartingStrategies.BALANCED_BY_POSITION_NAME;
    private static final int STARTING_STRATEGIES = 2;

    private static final String VALID_WEIGHTS = """
            {"GOALS": 0.30, "ASSISTS": 0.20, "SHOTS_ON_TARGET": 0.10, "KEY_PASSES": 0.10,
             "SUCCESSFUL_DRIBBLES": 0.10, "RATING": 0.20}""";
    private static final String OTHER_WEIGHTS = """
            {"GOALS": 0.50, "RATING": 0.50}""";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:strategy-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private StrategySQLDAO strategyDAO;

    @Autowired
    private StrategySeeder seeder;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        database = new QuoteDatabase(context);
        resetStrategies();
        adminToken = new AuthTestHelper(mvc).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @AfterEach
    void cleanDatabase() {
        resetStrategies();
    }

    @Test
    void alArrancarEstanLaAActivaYLaBEnSuVersionUnoConLosPesosDeLaA() throws Exception {
        MvcTestResult result = get(STRATEGIES_PATH);
        JsonNode strategies = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(strategies.values()).extracting(strategy -> strategy.get("name").asString())
                .containsExactly(MATCH_PERFORMANCE, BALANCED);
        JsonNode first = strategies.get(0);
        assertThat(first.get("active").asBoolean()).isTrue();
        assertThat(first.get("versions").values()).singleElement().satisfies(version -> {
            assertThat(version.get("version").asInt()).isOne();
            assertThat(version.get("active").asBoolean()).isTrue();
            assertThat(version.get("scaleFactor").decimalValue()).isEqualByComparingTo("10");
            assertThat(version.get("minimumValue").decimalValue()).isEqualByComparingTo("10.00");
            JsonNode weights = version.get("defaultWeights");
            assertThat(weights.propertyNames()).containsExactly("GOALS", "ASSISTS", "SHOTS", "KEY_PASSES",
                    "SUCCESSFUL_DRIBBLES", "TACKLES", "RATING");
            assertThat(weights.get("GOALS").decimalValue()).isEqualByComparingTo("0.25");
            assertThat(weights.get("ASSISTS").decimalValue()).isEqualByComparingTo("0.15");
            assertThat(weights.get("SHOTS").decimalValue()).isEqualByComparingTo("0.10");
            assertThat(weights.get("KEY_PASSES").decimalValue()).isEqualByComparingTo("0.10");
            assertThat(weights.get("SUCCESSFUL_DRIBBLES").decimalValue()).isEqualByComparingTo("0.10");
            assertThat(weights.get("TACKLES").decimalValue()).isEqualByComparingTo("0.10");
            assertThat(weights.get("RATING").decimalValue()).isEqualByComparingTo("0.20");
            assertThat(version.get("positionWeights").isEmpty()).isTrue();
            assertThat(version.get("createdAt").isNull()).isFalse();
        });
        JsonNode second = strategies.get(1);
        assertThat(second.get("active").asBoolean()).isFalse();
        assertThat(second.get("versions").values()).singleElement()
                .satisfies(version -> assertThat(version.get("active").asBoolean()).isFalse());
    }

    @Test
    void crearUnaValidaDevuelve201ConLaVersionUnoInactivaYNoCambiaLaActiva() throws Exception {
        MvcTestResult result = post(STRATEGIES_PATH, """
                {"name": "Ofensiva", "scaleFactor": 8, "minimumValue": 5.50, "defaultWeights": %s,
                 "positionWeights": {"GOALKEEPER": {"RATING": 0.60, "CLEAN_SHEETS": 0.40}}}
                """.formatted(VALID_WEIGHTS));
        JsonNode created = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("name").asString()).isEqualTo("Ofensiva");
        assertThat(created.get("active").asBoolean()).isFalse();
        assertThat(created.get("versions").values()).singleElement().satisfies(version -> {
            assertThat(version.get("version").asInt()).isOne();
            assertThat(version.get("active").asBoolean()).isFalse();
            assertThat(version.get("scaleFactor").decimalValue()).isEqualByComparingTo("8");
            assertThat(version.get("minimumValue").decimalValue()).isEqualByComparingTo("5.50");
            assertThat(version.get("defaultWeights").get("SHOTS_ON_TARGET").decimalValue()).isEqualByComparingTo("0.10");
            assertThat(version.get("positionWeights").get("GOALKEEPER").get("CLEAN_SHEETS").decimalValue())
                    .isEqualByComparingTo("0.40");
        });
        assertThat(activeName()).isEqualTo(MATCH_PERFORMANCE);
        assertThat(get(STRATEGIES_PATH + "/" + created.get("id").asLong()).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void crearSinFactorNiMinimoQuedaConFactorDiezYMinimoDiez() throws Exception {
        MvcTestResult result = post(STRATEGIES_PATH, """
                {"name": "Sin parametros", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));
        JsonNode version = body(result).get("versions").get(0);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(version.get("scaleFactor").decimalValue()).isEqualByComparingTo("10");
        assertThat(version.get("minimumValue").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void pesosQueSuman095SeRechazanConElMensajeYNoSeCreaNada() throws Exception {
        assertRejected("""
                {"name": "Corta", "defaultWeights": {"GOALS": 0.50, "RATING": 0.45}}
                """, "Los pesos por defecto deben sumar 1,00 y suman 0,95.");
    }

    @Test
    void pesosDeUnaPosicionQueNoSumanSeRechazanIndicandoLaPosicion() throws Exception {
        assertRejected("""
                {"name": "Corta", "defaultWeights": {"GOALS": 1.00},
                 "positionWeights": {"FORWARD": {"GOALS": 0.50, "RATING": 0.45}}}
                """, "Los pesos de delanteros deben sumar 1,00 y suman 0,95.");
    }

    @Test
    void unPesoNegativoSeRechaza() throws Exception {
        assertRejected("""
                {"name": "Negativa", "defaultWeights": {"GOALS": -0.10, "RATING": 1.10}}
                """, "El peso de GOALS no puede ser negativo.");
    }

    @Test
    void unPesoConTresDecimalesSeRechaza() throws Exception {
        assertRejected("""
                {"name": "Precisa", "defaultWeights": {"GOALS": 0.333, "RATING": 0.667}}
                """, "El peso de GOALS admite a lo sumo dos decimales.");
    }

    @Test
    void unaMetricaDesconocidaSeRechazaIndicandoSuNombre() throws Exception {
        assertRejected("""
                {"name": "En castellano", "defaultWeights": {"GOLES": 0.50, "RATING": 0.50}}
                """, "La métrica 'GOLES' no existe.");
    }

    @Test
    void unFactorDeEscalaCeroSeRechaza() throws Exception {
        assertRejected("""
                {"name": "Plana", "scaleFactor": 0, "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS), "El factor de escala debe ser mayor que 0.");
    }

    @Test
    void unMinimoDeCeroSeRechaza() throws Exception {
        assertRejected("""
                {"name": "Sin piso", "minimumValue": 0.00, "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS), "La cotización mínima debe ser al menos 0,01.");
    }

    @Test
    void sinPesosPorDefectoSeRechaza() throws Exception {
        assertRejected("""
                {"name": "Sin pesos"}
                """, "Los pesos por defecto son obligatorios.");
    }

    @Test
    void unNombreVacioSeRechazaSinCrearNada() throws Exception {
        MvcTestResult result = post(STRATEGIES_PATH, """
                {"name": "   ", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("violations").values())
                .anySatisfy(violation -> assertThat(violation.get("field").asString()).isEqualTo("name"));
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
    }

    @Test
    void unNombreRepetidoSalvoMayusculasYEspaciosRespondeConflicto() throws Exception {
        MvcTestResult result = post(STRATEGIES_PATH, """
                {"name": "rendimiento de PARTIDO ", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(result).get("message").asString())
                .isEqualTo("Ya existe una estrategia con el nombre 'rendimiento de PARTIDO'.");
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
    }

    @Test
    void editarUnaInactivaCreaLaVersionDosDejaIntactaLaUnoYNoCambiaLaActiva() throws Exception {
        long balancedId = idOf(BALANCED);
        JsonNode before = body(get(STRATEGIES_PATH + "/" + balancedId)).get("versions").get(0);

        MvcTestResult result = post(STRATEGIES_PATH + "/" + balancedId + "/versions", """
                {"scaleFactor": 6, "defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        JsonNode change = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(change.get("requotation").isNull()).isTrue();
        JsonNode strategy = change.get("strategy");
        assertThat(strategy.get("id").asLong()).isEqualTo(balancedId);
        assertThat(strategy.get("active").asBoolean()).isFalse();
        assertThat(strategy.get("versions").values()).extracting(version -> version.get("version").asInt())
                .containsExactly(1, 2);
        assertThat(strategy.get("versions").get(0)).isEqualTo(before);
        JsonNode second = strategy.get("versions").get(1);
        assertThat(second.get("active").asBoolean()).isFalse();
        assertThat(second.get("scaleFactor").decimalValue()).isEqualByComparingTo("6");
        assertThat(second.get("minimumValue").decimalValue()).isEqualByComparingTo(before.get("minimumValue").decimalValue());
        assertThat(second.get("defaultWeights").get("GOALS").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(second.get("positionWeights").isEmpty()).isTrue();
        assertThat(activeName()).isEqualTo(MATCH_PERFORMANCE);
    }

    @Test
    void editarLaActivaDejaActivaLaVersionNueva() throws Exception {
        long matchPerformanceId = idOf(MATCH_PERFORMANCE);

        MvcTestResult result = post(STRATEGIES_PATH + "/" + matchPerformanceId + "/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        JsonNode strategy = body(result).get("strategy");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(strategy.get("active").asBoolean()).isTrue();
        assertThat(strategy.get("versions").values())
                .extracting(version -> version.get("version").asInt(), version -> version.get("active").asBoolean())
                .containsExactly(tuple(1, false), tuple(2, true));
        assertThat(activeRows()).singleElement().satisfies(row -> {
            assertThat(row.getName()).isEqualTo(MATCH_PERFORMANCE);
            assertThat(row.getVersionNumber()).isEqualTo(2);
        });
    }

    @Test
    void editarConPesosInvalidosNoCreaNingunaVersion() throws Exception {
        long matchPerformanceId = idOf(MATCH_PERFORMANCE);

        MvcTestResult result = post(STRATEGIES_PATH + "/" + matchPerformanceId + "/versions", """
                {"defaultWeights": {"GOALS": 0.50, "RATING": 0.45}}
                """);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).isEqualTo("Los pesos por defecto deben sumar 1,00 y suman 0,95.");
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
    }

    @Test
    void activarOtraLaDejaActivaYDesactivaLaAnterior() throws Exception {
        long balancedId = idOf(BALANCED);

        MvcTestResult result = post(STRATEGIES_PATH + "/" + balancedId + "/activate", null);
        JsonNode change = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(change.get("requotation").isNull()).isTrue();
        assertThat(change.get("strategy").get("id").asLong()).isEqualTo(balancedId);
        assertThat(change.get("strategy").get("active").asBoolean()).isTrue();
        assertThat(activeName()).isEqualTo(BALANCED);
        assertThat(activeRows()).hasSize(1);
    }

    @Test
    void activarConCuerpoVacioYActivarLaYaActivaNoCambianNada() throws Exception {
        long matchPerformanceId = idOf(MATCH_PERFORMANCE);
        Long activeRowId = activeRows().getFirst().getId();

        MvcTestResult result = post(STRATEGIES_PATH + "/" + matchPerformanceId + "/activate", "{}");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(result).get("strategy").get("active").asBoolean()).isTrue();
        assertThat(activeRows()).extracting(StrategySQL::getId).containsExactly(activeRowId);
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
    }

    @Test
    void lasCotizacionesGuardadasConservanLaVersionUnoTrasEditarLaAYCrearOEditarOtraNoLasCambia() throws Exception {
        Player ndiaye = quotedPlayers();
        long matchPerformanceId = idOf(MATCH_PERFORMANCE);
        List<Quote> before = database.quotes();

        post(STRATEGIES_PATH, """
                {"name": "Ofensiva", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));
        post(STRATEGIES_PATH + "/" + idOf(BALANCED) + "/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(before);
        MvcTestResult edited = post(STRATEGIES_PATH + "/" + matchPerformanceId + "/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        JsonNode history = body(mvc.get().uri("/players/" + ndiaye.id() + "/quotes").exchange());

        assertThat(edited.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(history.get("content").values()).hasSize(2).allSatisfy(quote -> {
            assertThat(quote.get("strategy").get("id").asLong()).isEqualTo(matchPerformanceId);
            assertThat(quote.get("strategy").get("version").asInt()).isOne();
        });
    }

    @Test
    void enTodoMomentoHayExactamenteUnaActiva() throws Exception {
        long matchPerformanceId = idOf(MATCH_PERFORMANCE);
        long balancedId = idOf(BALANCED);
        assertThat(activeRows()).hasSize(1);

        post(STRATEGIES_PATH, """
                {"name": "Ofensiva", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));
        assertThat(activeRows()).hasSize(1);
        post(STRATEGIES_PATH + "/" + balancedId + "/activate", null);
        assertThat(activeRows()).hasSize(1);
        post(STRATEGIES_PATH + "/" + balancedId + "/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        assertThat(activeRows()).hasSize(1);
        post(STRATEGIES_PATH + "/" + matchPerformanceId + "/activate", null);

        JsonNode strategies = body(get(STRATEGIES_PATH));
        assertThat(activeRows()).hasSize(1);
        assertThat(strategies.values()).filteredOn(strategy -> strategy.get("active").asBoolean())
                .singleElement().satisfies(strategy -> assertThat(strategy.get("name").asString()).isEqualTo(MATCH_PERFORMANCE));
        assertThat(strategies.values()).flatMap(strategy -> strategy.get("versions").values())
                .filteredOn(version -> version.get("active").asBoolean())
                .hasSize(1);
    }

    @Test
    void elListadoMuestraCadaEstrategiaConTodasSusVersionesYCualEsLaActiva() throws Exception {
        long balancedId = idOf(BALANCED);
        post(STRATEGIES_PATH + "/" + balancedId + "/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        post(STRATEGIES_PATH + "/" + balancedId + "/activate", null);
        post(STRATEGIES_PATH, """
                {"name": "Ofensiva", "defaultWeights": %s}
                """.formatted(VALID_WEIGHTS));

        JsonNode strategies = body(get(STRATEGIES_PATH));

        assertThat(strategies.values())
                .extracting(strategy -> strategy.get("name").asString(), strategy -> strategy.get("active").asBoolean(),
                        strategy -> strategy.get("versions").size())
                .containsExactly(
                        tuple(MATCH_PERFORMANCE, false, 1),
                        tuple(BALANCED, true, 2),
                        tuple("Ofensiva", false, 1));
        assertThat(strategies.get(1).get("versions").values())
                .extracting(version -> version.get("active").asBoolean())
                .containsExactly(false, true);
    }

    @Test
    void unaEstrategiaInexistenteRespondeNotFoundAlConsultarEditarYActivar() throws Exception {
        MvcTestResult read = get(STRATEGIES_PATH + "/99999");
        MvcTestResult edited = post(STRATEGIES_PATH + "/99999/versions", """
                {"defaultWeights": %s}
                """.formatted(OTHER_WEIGHTS));
        MvcTestResult activated = post(STRATEGIES_PATH + "/99999/activate", null);

        assertThat(read.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(body(read).get("message").asString()).isEqualTo("No existe una estrategia con id 99999.");
        assertThat(edited.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(activated.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
    }

    @Test
    void unIdentificadorInvalidoRespondeBadRequest() throws Exception {
        assertThat(get(STRATEGIES_PATH + "/abc").getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(get(STRATEGIES_PATH + "/0").getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private void assertRejected(String requestBody, String message) throws Exception {
        MvcTestResult result = post(STRATEGIES_PATH, requestBody);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).isEqualTo(message);
        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
        assertThat(activeName()).isEqualTo(MATCH_PERFORMANCE);
    }

    /**
     * Dos delanteros con un partido en la última semana cerrada, cotizados con la A en su versión 1
     * por el recálculo manual. Devuelve uno de los dos.
     */
    private Player quotedPlayers() throws Exception {
        QuoteWeek lastClosed = QuoteWeek.lastClosedAt(Instant.now());
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Match match = database.finishedMatch("560600", lastClosed, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90));
        MvcTestResult recalculated = post(RECALCULATE_PATH, null);
        assertThat(recalculated.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(database.quotes()).hasSize(4)
                .allSatisfy(quote -> assertThat(quote.strategy().version()).isOne());
        return ndiaye;
    }

    private long idOf(String name) throws Exception {
        for (JsonNode strategy : body(get(STRATEGIES_PATH)).values()) {
            if (strategy.get("name").asString().equals(name)) {
                return strategy.get("id").asLong();
            }
        }
        throw new AssertionError("No está la estrategia " + name);
    }

    private String activeName() {
        return activeRows().getFirst().getName();
    }

    private List<StrategySQL> activeRows() {
        return strategyDAO.findAll().stream().filter(StrategySQL::isActive).toList();
    }

    /**
     * Deja la base como recién arrancada: sin cotizaciones ni datos de partidos, y con las dos
     * estrategias de arranque en su versión 1 (las versiones se borran de la última a la primera
     * por la clave a la raíz).
     */
    private void resetStrategies() {
        database.clean();
        strategyDAO.deleteAll(strategyDAO.findAll().stream()
                .sorted(Comparator.comparingInt(StrategySQL::getVersionNumber).reversed())
                .toList());
        seeder.run(new DefaultApplicationArguments());
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
