package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
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

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El score en toda cotización de la API pública (US8, FR-058), sin credencial, contra una H2 propia
 * y con el reloj quieto el 15/10/2026. Kane arranca con la inicial de 100,00 (score 0) el 17/08 y
 * cotiza 103,00 con score 0,30 desde el 24/08. Ninguna respuesta lleva el valor base, estadísticas
 * crudas ni métricas normalizadas (SC-014).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-score-e2e;DB_CLOSE_DELAY=-1")
class QuoteScoreIT {
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final String INITIAL_SCORE = "0.000000";
    private static final String CALCULATED_SCORE = "0.300000";

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
    private QuoteCacheInvalidator cacheInvalidator;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private Player kane;

    @BeforeEach
    void setUp() {
        database = new QuoteDatabase(context);
        database.clean();
        cacheInvalidator.invalidateAll();
        StrategyVersionRef strategy = strategyRepository.findCatalog().activeVersion().ref();
        kane = database.player("8300", "Harry Kane", Position.FORWARD, database.team("5", "FC Bayern München"));
        quoteRepository.saveAll(List.of(
                new Quote(null, kane.id(), WEEK, WEEK.start(), Quote.INITIAL_VALUE, null,
                        new BigDecimal(INITIAL_SCORE), QuoteType.INITIAL, strategy),
                new Quote(null, kane.id(), WEEK, WEEK.end(), new BigDecimal("103.00"), Quote.INITIAL_VALUE,
                        new BigDecimal(CALCULATED_SCORE), QuoteType.CALCULATED, strategy)));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void laCotizacionActualTraeElScoreDeLaCalculada() throws Exception {
        MvcTestResult result = get("/players/" + kane.id() + "/quotes/current");

        assertThat(raw(result)).contains("\"score\":0.300000");
        assertThat(json(result).get("score").decimalValue()).isEqualByComparingTo(CALCULATED_SCORE);
    }

    @Test
    void laDeUnaFechaTraeElScoreDeLaQueRegia() throws Exception {
        JsonNode atInitial = json(get("/players/" + kane.id() + "/quotes/at?date=2026-08-20"));
        JsonNode atCalculated = json(get("/players/" + kane.id() + "/quotes/at?date=2026-08-25"));

        assertThat(atInitial.get("type").asString()).isEqualTo("INITIAL");
        assertThat(atInitial.get("score").decimalValue()).isEqualByComparingTo(INITIAL_SCORE);
        assertThat(atCalculated.get("score").decimalValue()).isEqualByComparingTo(CALCULATED_SCORE);
    }

    @Test
    void laEvolucionTraeElScoreDeCadaCotizacion() throws Exception {
        MvcTestResult result = get("/players/" + kane.id() + "/quotes");

        assertThat(raw(result)).contains("\"score\":0.000000", "\"score\":0.300000");
        assertThat(json(result).get("content").values()).extracting(quote -> quote.get("score").decimalValue())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal(INITIAL_SCORE), new BigDecimal(CALCULATED_SCORE));
    }

    @Test
    void elLoteDeLaPizarraTraeElScore() throws Exception {
        JsonNode body = json(get("/players/quotes/current?ids=" + kane.id()));

        assertThat(body.get("quotes").values()).singleElement().satisfies(quote -> {
            assertThat(quote.get("playerId").asLong()).isEqualTo(kane.id());
            assertThat(quote.get("score").decimalValue()).isEqualByComparingTo(CALCULATED_SCORE);
        });
    }

    @Test
    void elRankingTraeElScoreDeCadaFila() throws Exception {
        JsonNode body = json(get("/players/ranking"));

        assertThat(body.get("content").values()).singleElement()
                .satisfies(row -> assertThat(row.get("quote").get("score").decimalValue())
                        .isEqualByComparingTo(CALCULATED_SCORE));
    }

    @Test
    void ningunaTraeElValorBaseEstadisticasNiMetricasNormalizadasSC014() throws Exception {
        List<MvcTestResult> results = List.of(
                get("/players/" + kane.id() + "/quotes/current"),
                get("/players/" + kane.id() + "/quotes/at?date=2026-08-25"),
                get("/players/" + kane.id() + "/quotes"),
                get("/players/quotes/current?ids=" + kane.id()),
                get("/players/ranking"));

        assertThat(results).allSatisfy(result -> {
            String body = raw(result).toLowerCase(Locale.ROOT);
            assertThat(body).contains("\"score\"");
            assertThat(body).doesNotContain("basevalue", "goals", "minutes", "rating", "normaliz", "metric",
                    "weight", "stats");
            for (ValuationMetric metric : ValuationMetric.values()) {
                assertThat(body).doesNotContain("\"" + metric.name().toLowerCase(Locale.ROOT) + "\"");
            }
            assertThat(fieldNames(jsonMapper.readTree(raw(result))))
                    .doesNotContain("baseValue", "goals", "minutesPlayed", "rating");
        });
    }

    private MvcTestResult get(String uri) {
        MvcTestResult result = mvc.get().uri(uri).exchange();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return result;
    }

    private JsonNode json(MvcTestResult result) {
        return jsonMapper.readTree(raw(result));
    }

    /**
     * Los nombres de todos los campos de la respuesta, en cualquier nivel.
     */
    private static List<String> fieldNames(JsonNode node) {
        List<String> own = node.isObject() ? List.copyOf(node.propertyNames()) : List.of();
        List<String> nested = StreamSupport.stream(node.values().spliterator(), false)
                .flatMap(child -> fieldNames(child).stream())
                .toList();
        return Stream.concat(own.stream(), nested.stream()).toList();
    }

    private static String raw(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
