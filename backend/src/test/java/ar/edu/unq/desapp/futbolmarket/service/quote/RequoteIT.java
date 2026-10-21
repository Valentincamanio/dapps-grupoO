package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyChangeResult;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyChangeService;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyService;

/**
 * Recotizar en el acto de punta a punta contra la H2, con el reloj quieto (US5).
 *
 * <p>Cinco delanteros de cuatro equipos juegan las semanas del 17/08, del 24/08 y del 31/08 de
 * 2026. Las estrategias del test valen factor 10 y mínimo 10,00, y están armadas para que Ndiaye
 * reproduzca el ejemplo del spec:</p>
 *
 * <ul>
 *     <li>A (mitad goles, mitad quites): +0,30 en la primera semana (103,00) y −0,20 en la segunda
 *     (101,00).</li>
 *     <li>B (un cuarto goles, tres cuartos quites): +0,10 en la segunda (104,00 desde 103,00) y
 *     +0,20 en la tercera (106,00 desde la recotizada de 104,00).</li>
 *     <li>C (solo quites): +0,40 en la segunda (107,00 desde 103,00).</li>
 * </ul>
 *
 * <p>El reloj arranca el miércoles de la tercera semana: la segunda es la última cerrada.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:requote-it;DB_CLOSE_DELAY=-1")
class RequoteIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final QuoteWeek THIRD = SECOND.next();
    private static final QuoteWeek FOURTH = THIRD.next();
    private static final Instant DURING_THIRD = THIRD.start().plus(Duration.ofDays(2)).plus(Duration.ofHours(15));
    private static final AtomicInteger EDITABLE_NAMES = new AtomicInteger();

    private static final WeightSet WEIGHTS_A = weights("0.50", "0.50");
    private static final WeightSet WEIGHTS_B = weights("0.25", "0.75");
    private static final WeightSet WEIGHTS_C = weights("0.00", "1.00");

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(DURING_THIRD);
        }
    }

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private QuoteQueryService queryService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyChangeService changeService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private long strategyA;
    private long strategyB;
    private long strategyC;
    private Player ndiaye;
    private List<Player> forwards;

    @BeforeEach
    void prepareDatabase() {
        clock.set(DURING_THIRD);
        database = new QuoteDatabase(context);
        database.clean();
        strategyA = strategyNamed("Recotizar A", WEIGHTS_A);
        strategyB = strategyNamed("Recotizar B", WEIGHTS_B);
        strategyC = strategyNamed("Recotizar C", WEIGHTS_C);
        changeService.activate(strategyA, RequoteTiming.NEXT_WEEK);
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void activarBAhoraRecotizaLaUltimaSemanaDesdeElPrecioPrevioYConservaLaCalculada() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        StrategyChangeResult result = changeService.activate(strategyB, RequoteTiming.NOW);

        RecalculationReport report = result.requotation().orElseThrow();
        assertThat(report.origin()).isEqualTo(RecalculationOrigin.STRATEGY_CHANGE);
        assertThat(report.strategy().strategyId()).isEqualTo(strategyB);
        assertThat(report.quotedWeeks()).containsExactly(new WeekResult(SECOND, 0, 0, 5));
        assertThat(report.pendingWeeks()).isEmpty();
        assertThat(historyOf(ndiaye)).extracting(Quote::type, Quote::week, Quote::value, Quote::validFrom)
                .containsExactly(
                        tuple(QuoteType.INITIAL, FIRST, value("100.00"), FIRST.start()),
                        tuple(QuoteType.CALCULATED, FIRST, value("103.00"), FIRST.end()),
                        tuple(QuoteType.CALCULATED, SECOND, value("101.00"), SECOND.end()),
                        tuple(QuoteType.REQUOTED, SECOND, value("104.00"), DURING_THIRD));
        Quote requoted = historyOf(ndiaye).getLast();
        assertThat(requoted.baseValue()).isEqualTo(value("103.00"));
        assertThat(requoted.score()).isEqualTo(new BigDecimal("0.100000"));
        assertThat(requoted.strategy().strategyId()).isEqualTo(strategyB);
        assertThat(queryService.getCurrent(ndiaye.id()).value()).isEqualTo(value("104.00"));
        assertThat(strategyRepository.findCatalog().activeVersion().strategyId()).isEqualTo(strategyB);
    }

    @Test
    void repetirLaRecotizacionNoSumaFilasYElJugadorSigueEn104() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        changeService.activate(strategyB, RequoteTiming.NOW);
        long afterFirst = database.quoteCount();
        clock.set(DURING_THIRD.plus(Duration.ofHours(1)));

        StrategyChangeResult again = changeService.activate(strategyB, RequoteTiming.NOW);

        assertThat(again.requotation()).isEmpty();
        assertThat(database.quoteCount()).isEqualTo(afterFirst);
        assertThat(queryService.getCurrent(ndiaye.id()).value()).isEqualTo(value("104.00"));
    }

    @Test
    void recotizarConOtraEstrategiaParteDe103YNoDe104() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        changeService.activate(strategyB, RequoteTiming.NOW);
        Instant later = DURING_THIRD.plus(Duration.ofHours(1));
        clock.set(later);

        changeService.activate(strategyC, RequoteTiming.NOW);

        Quote withC = historyOf(ndiaye).getLast();
        assertThat(withC.type()).isEqualTo(QuoteType.REQUOTED);
        assertThat(withC.validFrom()).isEqualTo(later);
        assertThat(withC.baseValue()).isEqualTo(value("103.00"));
        assertThat(withC.value()).isEqualTo(value("107.00"));
        assertThat(withC.strategy().strategyId()).isEqualTo(strategyC);
        assertThat(database.quotesOf(SECOND)).filteredOn(quote -> quote.type() == QuoteType.REQUOTED).hasSize(10);
    }

    @Test
    void siLaUltimaSemanaYaSeCotizoConLaNuevaNoSeRepite() {
        loadSeason();
        changeService.activate(strategyB, RequoteTiming.NEXT_WEEK);
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        changeService.activate(strategyA, RequoteTiming.NEXT_WEEK);
        long before = database.quoteCount();

        StrategyChangeResult result = changeService.activate(strategyB, RequoteTiming.NOW);

        assertThat(result.requotation()).hasValueSatisfying(report -> {
            assertThat(report.quotedWeeks()).isEmpty();
            assertThat(report.isEmpty()).isTrue();
        });
        assertThat(database.quoteCount()).isEqualTo(before);
        assertThat(strategyRepository.findCatalog().activeVersion().strategyId()).isEqualTo(strategyB);
    }

    @Test
    void lasSemanasPendientesSeCotizanConLaNuevaAntesDeRecotizar() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        clock.set(FOURTH.start().plus(Duration.ofHours(9)));

        StrategyChangeResult result = changeService.activate(strategyB, RequoteTiming.NOW);

        RecalculationReport report = result.requotation().orElseThrow();
        assertThat(report.quotedWeeks()).containsExactly(new WeekResult(THIRD, 0, 5, 0));
        assertThat(database.quotesOf(THIRD)).allSatisfy(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
            assertThat(quote.strategy().strategyId()).isEqualTo(strategyB);
        });
        assertThat(database.quotesOf(SECOND)).noneMatch(quote -> quote.type() == QuoteType.REQUOTED);
    }

    @Test
    void editarLaActivaConAhoraRecotizaConLaVersionNueva() {
        loadSeason();
        long editable = strategyNamed("Recotizar editable " + EDITABLE_NAMES.incrementAndGet(), WEIGHTS_A);
        changeService.activate(editable, RequoteTiming.NEXT_WEEK);
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        StrategyChangeResult result = changeService.createVersion(editable, null, null, WEIGHTS_B, Map.of(),
                RequoteTiming.NOW);

        assertThat(result.strategy().active()).isTrue();
        assertThat(result.strategy().strategy().versions()).extracting(StrategyVersion::version).containsExactly(1, 2);
        Quote requoted = historyOf(ndiaye).getLast();
        assertThat(requoted.type()).isEqualTo(QuoteType.REQUOTED);
        assertThat(requoted.value()).isEqualTo(value("104.00"));
        assertThat(requoted.strategy().strategyId()).isEqualTo(editable);
        assertThat(requoted.strategy().version()).isEqualTo(2);
        assertThat(calculatedOf(ndiaye, SECOND).strategy().version()).isEqualTo(1);
    }

    @Test
    void sinCotizacionesNiEstadisticasSoloCambiaLaActiva() {
        StrategyChangeResult result = changeService.activate(strategyB, RequoteTiming.NOW);

        assertThat(result.requotation()).hasValueSatisfying(report -> assertThat(report.isEmpty()).isTrue());
        assertThat(database.quoteCount()).isZero();
        assertThat(strategyRepository.findCatalog().activeVersion().strategyId()).isEqualTo(strategyB);
    }

    @Test
    void elCierreDeLaSemanaSiguienteParteDeLaRecotizada() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        changeService.activate(strategyB, RequoteTiming.NOW);
        clock.set(FOURTH.start().plus(Duration.ofHours(6)));

        recalculationService.recalculate(RecalculationOrigin.WEEKLY);

        Quote third = calculatedOf(ndiaye, THIRD);
        assertThat(third.score()).isEqualTo(new BigDecimal("0.200000"));
        assertThat(third.baseValue()).isEqualTo(value("104.00"));
        assertThat(third.value()).isEqualTo(value("106.00"));
    }

    @Test
    void laEvolucionMuestraLaCalculadaYLaRecotizadaDeLaMismaSemanaConSuTipoEstrategiaYVersion() {
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        changeService.activate(strategyB, RequoteTiming.NOW);

        List<Quote> history = queryService.getHistory(ndiaye.id(), null, null, 0, 20).content();

        assertThat(history).filteredOn(quote -> quote.week().equals(SECOND))
                .extracting(Quote::type, quote -> quote.strategy().strategyId(), quote -> quote.strategy().version(),
                        Quote::value)
                .containsExactly(
                        tuple(QuoteType.CALCULATED, strategyA, 1, value("101.00")),
                        tuple(QuoteType.REQUOTED, strategyB, 1, value("104.00")));
    }

    /**
     * Ndiaye, Beto (Everton), Zirkzee (Manchester United), Saka (Arsenal) y Salah (Liverpool), en
     * Everton - Manchester United y Arsenal - Liverpool cada semana. Los goles y quites de cada
     * semana son los que dan los scores de la descripción de la clase.
     */
    private void loadSeason() {
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Team arsenal = database.team("57", "Arsenal FC");
        Team liverpool = database.team("64", "Liverpool FC");
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player beto = database.player("8136", "Beto", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Player saka = database.player("8138", "Bukayo Saka", Position.FORWARD, arsenal);
        Player salah = database.player("8139", "Mohamed Salah", Position.FORWARD, liverpool);
        forwards = List.of(ndiaye, beto, zirkzee, saka, salah);

        playWeek(FIRST, everton, manUtd, arsenal, liverpool, new int[] {4, 0, 1, 2, 3}, new int[] {1, 1, 0, 2, 3});
        playWeek(SECOND, everton, manUtd, arsenal, liverpool, new int[] {0, 1, 2, 3, 4}, new int[] {3, 0, 1, 2, 4});
        playWeek(THIRD, everton, manUtd, arsenal, liverpool, new int[] {4, 0, 1, 2, 3}, new int[] {2, 0, 1, 3, 4});
    }

    /**
     * Goles y quites de la semana, en el orden de {@link #forwards}.
     */
    private void playWeek(QuoteWeek week, Team everton, Team manUtd, Team arsenal, Team liverpool, int[] goals,
                          int[] tackles) {
        String id = week.monday().toString().replace("-", "");
        Match evertonManUtd = database.finishedMatch(id + "1", week, everton, manUtd, goals[0] + goals[1], goals[2],
                true);
        Match arsenalLiverpool = database.finishedMatch(id + "2", week, arsenal, liverpool, goals[3], goals[4], true);
        List<Match> matches = List.of(evertonManUtd, evertonManUtd, evertonManUtd, arsenalLiverpool, arsenalLiverpool);
        for (int i = 0; i < forwards.size(); i++) {
            database.stats(stats().forPlayer(forwards.get(i)).inMatch(matches.get(i)).minutes(90)
                    .goals(goals[i]).tackles(tackles[i]));
        }
    }

    private long strategyNamed(String name, WeightSet defaultWeights) {
        Strategy existing = strategyRepository.findCatalog().findByName(name);
        if (existing != null) {
            return existing.id();
        }
        return strategyService.create(name, BigDecimal.TEN, new BigDecimal("10.00"), defaultWeights, Map.of())
                .strategy().id();
    }

    private List<Quote> historyOf(Player player) {
        return database.quotesOf(player).stream().sorted(Comparator.comparing(Quote::validFrom)).toList();
    }

    private Quote calculatedOf(Player player, QuoteWeek week) {
        return historyOf(player).stream()
                .filter(quote -> quote.type() == QuoteType.CALCULATED && quote.week().equals(week))
                .findFirst()
                .orElseThrow();
    }

    private static WeightSet weights(String goals, String tackles) {
        if (new BigDecimal(goals).signum() == 0) {
            return new WeightSet(Map.of(ValuationMetric.TACKLES, new BigDecimal(tackles)));
        }
        return new WeightSet(Map.of(ValuationMetric.GOALS, new BigDecimal(goals),
                ValuationMetric.TACKLES, new BigDecimal(tackles)));
    }

    private static BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
