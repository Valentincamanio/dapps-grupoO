package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La simulación de punta a punta, como administrador (US7): con una estrategia guardada o con
 * parámetros, los rechazos, y los filtros y la paginación del catálogo. Ninguna llamada guarda
 * cotizaciones.
 *
 * <p>Hay un partido con estadísticas en la última semana cerrada al momento de correr, así que se
 * simula una semana. Juegan Ndiaye (1 gol) y Zirkzee; Grealish y Kane (de la Bundesliga) no juegan.
 * Las estrategias son las que siembra {@code StrategySeeder}; los ids se leen del listado. Usa un
 * administrador al azar y una H2 propia.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulationControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String SIMULATE_PATH = "/quotes/simulate";
    private static final String STRATEGIES_PATH = "/strategies";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_REQUEST_MESSAGE =
            "Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna.";
    private static final String PARAMETERS = """
            {"parameters": {"scaleFactor": 5, "minimumValue": 10.00,
             "defaultWeights": {"GOALS": 0.60, "SHOTS": 0.20, "RATING": 0.20}}}""";
    private static final int PLAYERS = 4;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:simulation-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TeamRepository teamRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private String adminToken;
    private Player ndiaye;
    private Player kane;
    private Player grealish;

    @BeforeEach
    void setUp() throws Exception {
        database = new QuoteDatabase(context);
        database.clean();
        loadSeason();
        adminToken = new AuthTestHelper(mvc).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void conUnaEstrategiaGuardadaResponde200ConLaEstrategiaYUnaFilaPorJugadorActivo() throws Exception {
        long balanced = idOf(StartingStrategies.BALANCED_BY_POSITION_NAME);

        MvcTestResult result = simulate("{\"strategyId\": " + balanced + "}", "");
        JsonNode page = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(page.get("strategy").get("id").asLong()).isEqualTo(balanced);
        assertThat(page.get("strategy").get("name").asString()).isEqualTo(StartingStrategies.BALANCED_BY_POSITION_NAME);
        assertThat(page.get("strategy").get("version").asInt()).isOne();
        assertThat(page.get("weeksSimulated").asInt()).isOne();
        assertThat(page.get("pendingWeeks").values()).isEmpty();
        assertThat(page.get("totalElements").asLong()).isEqualTo(PLAYERS);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(10);
        assertThat(page.get("content").values()).hasSize(PLAYERS).allSatisfy(row -> {
            assertThat(row.get("currentValue").isNull()).isTrue();
            assertThat(row.get("difference").decimalValue())
                    .isEqualByComparingTo(row.get("simulatedValue").decimalValue().subtract(Quote.INITIAL_VALUE));
        });
        JsonNode first = page.get("content").values().iterator().next();
        assertThat(first.get("playerId").asLong()).isEqualTo(ndiaye.id());
        assertThat(first.get("name").asString()).isEqualTo("Iliman Ndiaye");
        assertThat(first.get("team").asString()).isEqualTo("Everton FC");
        assertThat(first.get("league").asString()).isEqualTo("PREMIER");
        assertThat(first.get("position").asString()).isEqualTo("FORWARD");
        assertThat(first.get("simulatedValue").decimalValue()).isGreaterThan(Quote.INITIAL_VALUE);
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void conParametrosResponde200ConEstrategiaNulaYNoLosGuarda() throws Exception {
        int strategiesBefore = body(get(STRATEGIES_PATH)).values().size();

        MvcTestResult result = simulate(PARAMETERS, "");
        JsonNode page = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(page.has("strategy")).isTrue();
        assertThat(page.get("strategy").isNull()).isTrue();
        assertThat(page.get("content").values()).hasSize(PLAYERS);
        assertThat(body(get(STRATEGIES_PATH)).values()).hasSize(strategiesBefore);
        assertThat(database.quoteCount()).isZero();
    }

    @Test
    void pesosQueNoSumanUnoResponden400() throws Exception {
        MvcTestResult result = simulate("""
                {"parameters": {"defaultWeights": {"GOALS": 0.50, "RATING": 0.45}}}""", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).contains("deben sumar 1,00");
    }

    @Test
    void unaMetricaDesconocidaResponde400() throws Exception {
        MvcTestResult result = simulate("""
                {"parameters": {"defaultWeights": {"GOLES": 1.00}}}""", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).isEqualTo("La métrica 'GOLES' no existe.");
    }

    @Test
    void unaEstrategiaInexistenteResponde404() throws Exception {
        MvcTestResult result = simulate("{\"strategyId\": 999999}", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(body(result).get("message").asString()).isEqualTo("No existe una estrategia con id 999999.");
    }

    @Test
    void lasDosCosasALaVezResponden400() throws Exception {
        long active = idOf(StartingStrategies.MATCH_PERFORMANCE_NAME);
        String both = "{\"strategyId\": " + active + ", " + PARAMETERS.substring(1);

        MvcTestResult result = simulate(both, "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).isEqualTo(INVALID_REQUEST_MESSAGE);
    }

    @Test
    void ningunaDeLasDosResponde400() throws Exception {
        MvcTestResult result = simulate("{}", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(result).get("message").asString()).isEqualTo(INVALID_REQUEST_MESSAGE);
    }

    @Test
    void unIdentificadorDeEstrategiaNoPositivoResponde400() throws Exception {
        MvcTestResult result = simulate("{\"strategyId\": 0}", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void filtraPorLigaEquipoYPosicionComoElCatalogo() throws Exception {
        assertThat(playerIds(simulate(PARAMETERS, "?league=BUNDESLIGA"))).containsExactly(kane.id());
        assertThat(playerIds(simulate(PARAMETERS, "?team=Everton FC&position=MIDFIELDER")))
                .containsExactly(grealish.id());
        assertThat(playerIds(simulate(PARAMETERS, "?league=PREMIER&position=FORWARD"))).hasSize(2);
    }

    @Test
    void paginaComoElCatalogo() throws Exception {
        List<Long> all = playerIds(simulate(PARAMETERS, ""));

        MvcTestResult result = simulate(PARAMETERS, "?page=1&size=3");
        JsonNode page = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(playerIds(result)).containsExactly(all.get(3));
        assertThat(page.get("totalElements").asLong()).isEqualTo(PLAYERS);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("hasPrevious").asBoolean()).isTrue();
        assertThat(page.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void unaPosicionInvalidaResponde400() throws Exception {
        MvcTestResult result = simulate(PARAMETERS, "?position=STRIKER");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void unaPaginacionFueraDeRangoResponde400() throws Exception {
        assertThat(simulate(PARAMETERS, "?size=51").getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(simulate(PARAMETERS, "?page=-1").getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private void loadSeason() {
        QuoteWeek lastClosed = QuoteWeek.lastClosedAt(Instant.now());
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Team bayern = teamRepository.save(new Team(null, "5", "FC Bayern München", null, League.BUNDESLIGA, "ws-5"));
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        grealish = database.player("8140", "Jack Grealish", Position.MIDFIELDER, everton);
        kane = database.player("8150", "Harry Kane", Position.FORWARD, bayern);
        Match match = database.finishedMatch("560600", lastClosed, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1).shots(3).rating("7.8"),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).shots(1).rating("6.2"));
    }

    private long idOf(String name) throws Exception {
        for (JsonNode strategy : body(get(STRATEGIES_PATH)).values()) {
            if (strategy.get("name").asString().equals(name)) {
                return strategy.get("id").asLong();
            }
        }
        throw new AssertionError("No está la estrategia " + name);
    }

    private List<Long> playerIds(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return body(result).get("content").values().stream().map(row -> row.get("playerId").asLong()).toList();
    }

    private MvcTestResult simulate(String requestBody, String query) {
        return mvc.post().uri(SIMULATE_PATH + query)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
