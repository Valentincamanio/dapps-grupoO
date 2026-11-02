package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las consultas públicas de cotizaciones de punta a punta, sin credencial, contra una H2 propia y
 * con el reloj quieto el jueves 15/10/2026 al mediodía de Argentina.
 *
 * <p>Las cotizaciones son las del ejemplo del spec: la semana del 17/08 arranca con la inicial de
 * 100,00 y cierra el 24/08; Kane tiene además la semana del 24/08, que cierra el 31/08. El caché se
 * vacía antes de cada test, porque los datos cambian entre tests y el contexto se comparte.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-controller-e2e;DB_CLOSE_DELAY=-1")
class QuoteControllerIT {
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.000000");
    private static final long MISSING_ID = 999_999L;
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private QuoteCacheInvalidator cacheInvalidator;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private StrategyVersionRef strategy;
    private Player ndiaye;
    private Player zirkzee;
    private Player kane;
    private Player pickford;
    private Player coleman;
    private Player beto;

    @BeforeEach
    void setUp() {
        database = new QuoteDatabase(context);
        database.clean();
        cacheInvalidator.invalidateAll();
        strategy = strategyRepository.findCatalog().activeVersion().ref();
        Team everton = teamRepository.save(new Team(null, "62", "Everton FC",
                "https://crests.football-data.org/62.png", League.PREMIER, "31"));
        Team manUtd = database.team("66", "Manchester United FC");
        Team bayern = teamRepository.save(new Team(null, "5", "FC Bayern München", null, League.BUNDESLIGA, "37"));
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        kane = database.player("8300", "Harry Kane", Position.FORWARD, bayern);
        pickford = database.player("8303", "Jordan Pickford", Position.GOALKEEPER, everton);
        coleman = database.inactive(database.player("8304", "Seamus Coleman", Position.DEFENDER, everton));
        beto = database.player("8305", "Beto", Position.FORWARD, everton);
        quoteRepository.saveAll(List.of(
                initial(ndiaye), calculated(ndiaye, FIRST, "103.00", "100.00"),
                initial(zirkzee),
                initial(kane), calculated(kane, FIRST, "110.00", "100.00"), calculated(kane, SECOND, "120.00", "110.00"),
                initial(pickford), calculated(pickford, FIRST, "99.00", "100.00"),
                initial(coleman), calculated(coleman, FIRST, "200.00", "100.00")));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void laCotizacionActualInformaValorPrecioVigenciaSemanaTipoEstrategiaYVariacion() throws Exception {
        MvcTestResult result = get("/players/" + ndiaye.id() + "/quotes/current");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        String raw = raw(result);
        assertThat(raw).contains("\"value\":103.00", "\"tokenPrice\":1.0300", "\"amount\":3.00", "\"percent\":3.00");
        JsonNode quote = jsonMapper.readTree(raw);
        assertThat(quote.get("playerId").asLong()).isEqualTo(ndiaye.id());
        assertThat(quote.get("weekStart").asString()).isEqualTo("2026-08-17");
        assertThat(quote.get("validFrom").asString()).isEqualTo("2026-08-24T03:00:00Z");
        assertThat(quote.get("type").asString()).isEqualTo("CALCULATED");
        assertThat(quote.get("strategy").get("id").asLong()).isEqualTo(strategy.strategyId());
        assertThat(quote.get("strategy").get("name").asString()).isEqualTo(strategy.name());
        assertThat(quote.get("strategy").get("version").asInt()).isEqualTo(strategy.version());
    }

    @Test
    void conSoloLaInicialNoInformaVariacion() throws Exception {
        MvcTestResult result = get("/players/" + zirkzee.id() + "/quotes/current");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode quote = body(result);
        assertThat(quote.get("type").asString()).isEqualTo("INITIAL");
        assertThat(quote.has("variation")).isFalse();
        assertThat(raw(result)).contains("\"value\":100.00", "\"tokenPrice\":1.0000");
    }

    @Test
    void unJugadorSinCotizacionYUnoInexistenteSonDosNotFoundDistintos() throws Exception {
        MvcTestResult withoutQuote = get("/players/" + beto.id() + "/quotes/current");
        MvcTestResult missing = get("/players/" + MISSING_ID + "/quotes/current");

        assertThat(withoutQuote.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(message(withoutQuote)).isEqualTo("El jugador " + beto.id() + " no tiene cotización.");
        assertThat(message(missing)).isEqualTo("No se encontró el jugador con id " + MISSING_ID + ".");
    }

    @Test
    void unJugadorInactivoNoEstaEnElRankingPeroSuCotizacionSeConsultaPorId() throws Exception {
        MvcTestResult current = get("/players/" + coleman.id() + "/quotes/current");
        MvcTestResult ranking = get("/players/ranking?size=50");

        assertThat(current.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(raw(current)).contains("\"value\":200.00");
        assertThat(rankingIds(body(ranking))).doesNotContain(coleman.id());
    }

    /**
     * El ejemplo de US2.6: al 20/08 rige la inicial, al 24/08 la calculada y al 16/08 todavía no
     * había ninguna.
     */
    @Test
    void laCotizacionAUnaFechaEsLaQueRegiaAlFinalDeEseDia() throws Exception {
        MvcTestResult august20 = get("/players/" + ndiaye.id() + "/quotes/at?date=2026-08-20");
        MvcTestResult august24 = get("/players/" + ndiaye.id() + "/quotes/at?date=2026-08-24");
        MvcTestResult august16 = get("/players/" + ndiaye.id() + "/quotes/at?date=2026-08-16");

        assertThat(august20.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(raw(august20)).contains("\"value\":100.00", "\"type\":\"INITIAL\"");
        assertThat(raw(august24)).contains("\"value\":103.00", "\"type\":\"CALCULATED\"");
        assertThat(august16.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(message(august16)).isEqualTo("El jugador " + ndiaye.id() + " no tiene cotización al 2026-08-16.");
    }

    @Test
    void unaFechaFuturaInvalidaOFaltanteResponde400ConElValorEnElMensaje() throws Exception {
        MvcTestResult future = get("/players/" + ndiaye.id() + "/quotes/at?date=2027-01-01");
        MvcTestResult invalid = get("/players/" + ndiaye.id() + "/quotes/at?date=2026-13-45");
        MvcTestResult missing = get("/players/" + ndiaye.id() + "/quotes/at");

        assertThat(future.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(future)).isEqualTo("La fecha 2027-01-01 es futura.");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(invalid)).isEqualTo("La fecha '2026-13-45' no es válida: use el formato AAAA-MM-DD.");
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(missing)).isEqualTo("La fecha es obligatoria: use el formato AAAA-MM-DD.");
    }

    @Test
    void laEvolucionIncluyeLosDosExtremosYSePagina() throws Exception {
        MvcTestResult first = get("/players/" + kane.id() + "/quotes?from=2026-08-24&to=2026-08-31&size=1");
        MvcTestResult second = get("/players/" + kane.id() + "/quotes?from=2026-08-24&to=2026-08-31&size=1&page=1");

        assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode firstPage = body(first);
        assertThat(firstPage.get("playerId").asLong()).isEqualTo(kane.id());
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(2);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.get("content").get(0).get("validFrom").asString()).isEqualTo("2026-08-24T03:00:00Z");
        assertThat(raw(first)).contains("\"value\":110.00");
        JsonNode secondPage = body(second);
        assertThat(secondPage.get("content").get(0).get("validFrom").asString()).isEqualTo("2026-08-31T03:00:00Z");
        assertThat(secondPage.get("hasPrevious").asBoolean()).isTrue();
    }

    @Test
    void laEvolucionSinRangoDevuelveTodoElHistorialAscendente() throws Exception {
        MvcTestResult result = get("/players/" + kane.id() + "/quotes");

        JsonNode page = body(result);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(StreamSupport.stream(page.get("content").spliterator(), false)
                .map(quote -> quote.get("type").asString()).toList())
                .containsExactly("INITIAL", "CALCULATED", "CALCULATED");
    }

    @Test
    void laEvolucionRechazaUnRangoInvertidoYUnaFechaInvalidaConElValorEnElMensaje() throws Exception {
        MvcTestResult inverted = get("/players/" + kane.id() + "/quotes?from=2026-09-01&to=2026-08-01");
        MvcTestResult invalid = get("/players/" + kane.id() + "/quotes?from=01-08-2026");
        MvcTestResult tooBig = get("/players/" + kane.id() + "/quotes?size=101");

        assertThat(inverted.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(inverted)).isEqualTo("El inicio del rango (2026-09-01) es posterior al final (2026-08-01).");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(invalid)).contains("'01-08-2026'");
        assertThat(tooBig.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void laEvolucionDeUnJugadorInexistenteEs404() throws Exception {
        MvcTestResult result = get("/players/" + MISSING_ID + "/quotes");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(message(result)).isEqualTo("No se encontró el jugador con id " + MISSING_ID + ".");
    }

    @Test
    void elRankingOrdenaPorCotizacionEInformaLaEstrategiaActiva() throws Exception {
        MvcTestResult result = get("/players/ranking");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode ranking = body(result);
        assertThat(rankingIds(ranking)).containsExactly(kane.id(), ndiaye.id(), zirkzee.id(), pickford.id());
        assertThat(ranking.get("activeStrategy").get("name").asString()).isEqualTo(strategy.name());
        assertThat(ranking.get("activeStrategy").get("version").asInt()).isEqualTo(strategy.version());
        assertThat(ranking.get("size").asInt()).isEqualTo(10);
        JsonNode first = ranking.get("content").get(0);
        assertThat(first.get("name").asString()).isEqualTo("Harry Kane");
        assertThat(first.get("team").asString()).isEqualTo("FC Bayern München");
        assertThat(first.get("league").asString()).isEqualTo("BUNDESLIGA");
        assertThat(first.get("position").asString()).isEqualTo("FORWARD");
        assertThat(first.get("quote").get("type").asString()).isEqualTo("CALCULATED");
        assertThat(raw(result)).contains("\"value\":120.00", "\"teamCrest\":\"https://crests.football-data.org/62.png\"");
    }

    @Test
    void elRankingSeFiltraYSePagina() throws Exception {
        JsonNode premierForwards = body(get("/players/ranking?league=PREMIER&position=FORWARD"));
        JsonNode everton = body(get("/players/ranking?team=Everton FC"));
        JsonNode secondPage = body(get("/players/ranking?size=2&page=1"));

        assertThat(rankingIds(premierForwards)).containsExactly(ndiaye.id(), zirkzee.id());
        assertThat(rankingIds(everton)).containsExactly(ndiaye.id(), pickford.id());
        assertThat(rankingIds(secondPage)).containsExactly(zirkzee.id(), pickford.id());
        assertThat(secondPage.get("totalElements").asLong()).isEqualTo(4);
        assertThat(secondPage.get("hasPrevious").asBoolean()).isTrue();
        assertThat(secondPage.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void elRankingRechazaUnaPosicionInexistente() throws Exception {
        MvcTestResult result = get("/players/ranking?position=DELANTERO");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(message(result)).isEqualTo(INVALID_PARAMETERS);
    }

    @Test
    void elLoteDevuelveSoloLosJugadoresConCotizacionEnElOrdenPedido() throws Exception {
        MvcTestResult result = get("/players/quotes/current?ids=" + zirkzee.id() + "," + beto.id() + "," + MISSING_ID
                + "," + ndiaye.id());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode quotes = body(result).get("quotes");
        assertThat(StreamSupport.stream(quotes.spliterator(), false).map(quote -> quote.get("playerId").asLong()).toList())
                .containsExactly(zirkzee.id(), ndiaye.id());
    }

    @Test
    void elLoteRechazaMasDe50IdsUnIdInvalidoOLaFaltaDeIds() throws Exception {
        String fiftyOne = LongStream.rangeClosed(1, 51).mapToObj(Long::toString).collect(Collectors.joining(","));

        MvcTestResult tooMany = get("/players/quotes/current?ids=" + fiftyOne);
        MvcTestResult notNumeric = get("/players/quotes/current?ids=1,abc");
        MvcTestResult zero = get("/players/quotes/current?ids=0");
        MvcTestResult missing = get("/players/quotes/current");

        assertThat(List.of(tooMany, notNumeric, zero, missing))
                .allSatisfy(result -> assertThat(result.getResponse().getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
        assertThat(message(tooMany)).isEqualTo(INVALID_PARAMETERS);
    }

    @Test
    void ningunaRespuestaTraeEstadisticasYTodasTraenElScore() throws Exception {
        List<MvcTestResult> results = List.of(
                get("/players/" + ndiaye.id() + "/quotes/current"),
                get("/players/" + ndiaye.id() + "/quotes/at?date=2026-08-24"),
                get("/players/" + ndiaye.id() + "/quotes"),
                get("/players/quotes/current?ids=" + ndiaye.id()),
                get("/players/ranking"));

        assertThat(results).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(raw(result)).doesNotContain("baseValue", "goals", "minutes", "rating");
            assertThat(raw(result)).contains("\"score\"");
        });
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private static String raw(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode body(MvcTestResult result) {
        return jsonMapper.readTree(raw(result));
    }

    private String message(MvcTestResult result) {
        return body(result).get("message").asString();
    }

    private static List<Long> rankingIds(JsonNode ranking) {
        return StreamSupport.stream(ranking.get("content").spliterator(), false)
                .map(row -> row.get("playerId").asLong())
                .toList();
    }

    private Quote initial(Player player) {
        return new Quote(null, player.id(), FIRST, FIRST.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategy);
    }

    private Quote calculated(Player player, QuoteWeek week, String value, String baseValue) {
        return new Quote(null, player.id(), week, week.end(), new BigDecimal(value), new BigDecimal(baseValue),
                ZERO_SCORE, QuoteType.CALCULATED, strategy);
    }
}
