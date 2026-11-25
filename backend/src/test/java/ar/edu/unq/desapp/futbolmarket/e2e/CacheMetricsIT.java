package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las métricas de los dos cachés de cotizaciones en {@code /actuator/prometheus} (US1 de la 013 y
 * contracts/metrics.md §2): existen desde el arranque, cuentan aciertos y fallos de las consultas
 * reales, no cambian con un pedido inválido y se ven solo como {@code ADMIN}. Los contadores son
 * acumulativos en el contexto compartido con los e2e de Actuator, así que cada caso compara contra
 * una lectura previa, y empieza con los cachés vacíos.
 *
 * <p>Nombres verificados con Micrometer 1.17.1 (research R4): {@code cache_gets_total},
 * {@code cache_puts_total}, {@code cache_evictions_total}, {@code cache_eviction_weight_total} y
 * {@code cache_size}.</p>
 */
class CacheMetricsIT extends ActuatorITSupport {

    private static final String RANKING = "quotes-ranking";
    private static final String CURRENT = "quotes-current";
    private static final List<String> CACHES = List.of(RANKING, CURRENT);
    private static final String GETS = "cache_gets_total";
    private static final String SIZE = "cache_size";
    private static final List<String> SERIES = List.of(GETS, "cache_puts_total", "cache_evictions_total",
            "cache_eviction_weight_total", SIZE);
    private static final String HIT = "result=\"hit\"";
    private static final String MISS = "result=\"miss\"";
    private static final String CACHE_MANAGER = "cache_manager=\"cacheManager\"";

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String RANKING_PATH = "/players/ranking?page=0&size=10";
    private static final String STRATEGIES_PATH = "/strategies";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final long MISSING_PLAYER = 990_001L;
    private static final int BURST = 1000;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private QuoteCacheInvalidator cacheInvalidator;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;
    private MarketDatabase database;
    private Player kane;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = new AuthTestHelper(mvc).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        cacheInvalidator.invalidateAll();
    }

    @AfterEach
    void clean() {
        database.clean();
        cacheInvalidator.invalidateAll();
    }

    @Test
    void lasSeriesDeLosDosCachesExistenConElNombreDelManager() throws Exception {
        List<String> samples = sampleLines();

        for (String cache : CACHES) {
            for (String series : SERIES) {
                assertThat(samples).as(series + " de " + cache).anyMatch(line -> line.startsWith(series + "{")
                        && line.contains(cacheLabel(cache)) && line.contains(CACHE_MANAGER));
            }
            assertThat(samples).as("aciertos de " + cache).anyMatch(line -> line.startsWith(GETS + "{")
                    && line.contains(cacheLabel(cache)) && line.contains(HIT));
            assertThat(samples).as("fallos de " + cache).anyMatch(line -> line.startsWith(GETS + "{")
                    && line.contains(cacheLabel(cache)) && line.contains(MISS));
        }
    }

    @Test
    void dosConsultasIgualesDelRankingSumanUnFalloYUnAciertoYRespondenLoMismo() throws Exception {
        double hitsBefore = gets(RANKING, HIT);
        double missesBefore = gets(RANKING, MISS);

        MvcTestResult first = mvc.get().uri(RANKING_PATH).exchange();
        MvcTestResult second = mvc.get().uri(RANKING_PATH).exchange();

        assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(gets(RANKING, MISS)).isEqualTo(missesBefore + 1);
        assertThat(gets(RANKING, HIT)).isEqualTo(hitsBefore + 1);
        assertThat(value(SIZE, cacheLabel(RANKING))).isEqualTo(1);
    }

    @Test
    void unPedidoInvalidoAlRankingRespondeElMismo400YNoTocaElCache() throws Exception {
        double hitsBefore = gets(RANKING, HIT);
        double missesBefore = gets(RANKING, MISS);

        MvcTestResult negativePage = mvc.get().uri("/players/ranking?page=-1").exchange();
        MvcTestResult hugePage = mvc.get().uri("/players/ranking?size=500").exchange();

        assertThat(negativePage.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(hugePage.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(gets(RANKING, HIT)).isEqualTo(hitsBefore);
        assertThat(gets(RANKING, MISS)).isEqualTo(missesBefore);
    }

    @Test
    void laCotizacionActualCuentaEnSuCacheYUnJugadorInexistenteNoOcupaEntrada() throws Exception {
        String path = "/players/" + kane.id() + "/quotes/current";
        double hitsBefore = gets(CURRENT, HIT);
        double missesBefore = gets(CURRENT, MISS);

        MvcTestResult first = mvc.get().uri(path).exchange();
        MvcTestResult second = mvc.get().uri(path).exchange();
        double sizeWithKane = value(SIZE, cacheLabel(CURRENT));
        MvcTestResult missing = mvc.get().uri("/players/" + MISSING_PLAYER + "/quotes/current").exchange();

        assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(gets(CURRENT, HIT)).isEqualTo(hitsBefore + 1);
        assertThat(gets(CURRENT, MISS)).isEqualTo(missesBefore + 2);
        assertThat(sizeWithKane).isEqualTo(1);
        assertThat(value(SIZE, cacheLabel(CURRENT))).isEqualTo(sizeWithKane);
    }

    @Test
    void unaRafagaDeMilConsultasIgualesSeRespondeCasiTodaDesdeElCache() throws Exception {
        double hitsBefore = gets(RANKING, HIT);
        double missesBefore = gets(RANKING, MISS);

        IntStream.range(0, BURST).forEach(index -> mvc.get().uri(RANKING_PATH).exchange());

        double hits = gets(RANKING, HIT) - hitsBefore;
        double misses = gets(RANKING, MISS) - missesBefore;
        assertThat(hits + misses).isEqualTo(BURST);
        assertThat(hits / (hits + misses)).isGreaterThanOrEqualTo(0.95);
        assertThat(misses).isEqualTo(1);
    }

    @Test
    void cambiarLaEstrategiaActivaPorHttpVaciaElCacheYLaConsultaSiguienteLaInforma() throws Exception {
        JsonNode cached = json(mvc.get().uri(RANKING_PATH).exchange());
        String previous = cached.get("activeStrategy").get("name").asString();
        JsonNode other = strategyOtherThan(previous);
        double missesBefore = gets(RANKING, MISS);

        try {
            MvcTestResult activated = activate(other.get("id").asLong());
            JsonNode fresh = json(mvc.get().uri(RANKING_PATH).exchange());

            assertThat(activated.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(fresh.get("activeStrategy").get("name").asString()).isEqualTo(other.get("name").asString());
            assertThat(gets(RANKING, MISS)).isEqualTo(missesBefore + 1);
        } finally {
            activate(strategyNamed(previous).get("id").asLong());
        }
    }

    @Test
    void sinCredencialLasMetricasDan401YConUnUsuarioDan403() throws Exception {
        TestUser user = new AuthTestHelper(mvc).registerUser();

        MvcTestResult anonymous = mvc.get().uri(PROMETHEUS_PATH).exchange();
        MvcTestResult asUser = mvc.get().uri(PROMETHEUS_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(asUser.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    // --- apoyo ---

    private static String cacheLabel(String cache) {
        return "cache=\"" + cache + "\"";
    }

    private double gets(String cache, String result) throws Exception {
        return value(GETS, cacheLabel(cache), result);
    }

    /** La suma de las series de esa métrica que tienen todas esas etiquetas. */
    private double value(String metric, String... labels) throws Exception {
        return sampleLines().stream()
                .filter(line -> line.startsWith(metric + "{"))
                .filter(line -> Arrays.stream(labels).allMatch(line::contains))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .sum();
    }

    private List<String> sampleLines() throws Exception {
        return mvc.get().uri(PROMETHEUS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange()
                .getResponse().getContentAsString().lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private MvcTestResult activate(long strategyId) {
        return mvc.post().uri(STRATEGIES_PATH + "/" + strategyId + "/activate")
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
    }

    private JsonNode strategyOtherThan(String name) throws Exception {
        return strategies().stream().filter(strategy -> !strategy.get("name").asString().equals(name))
                .findFirst().orElseThrow();
    }

    private JsonNode strategyNamed(String name) throws Exception {
        return strategies().stream().filter(strategy -> strategy.get("name").asString().equals(name))
                .findFirst().orElseThrow();
    }

    private List<JsonNode> strategies() throws Exception {
        return json(mvc.get().uri(STRATEGIES_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .exchange()).valueStream().toList();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
