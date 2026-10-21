package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ARSENAL_LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON_MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.SAKA;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ZIRKZEE;
import static org.assertj.core.api.Assertions.assertThat;

class WeeklyPerformanceTest {
    private static final EnumSet<ValuationMetric> REQUIRE_PLAYING = EnumSet.of(ValuationMetric.RATING,
            ValuationMetric.PASS_ACCURACY, ValuationMetric.TEAM_RESULT, ValuationMetric.CLEAN_SHEETS);

    /**
     * Everton 1 - 0 Liverpool, también el sábado de la semana de referencia.
     */
    private static final Match EVERTON_LIVERPOOL = QuoteFixtures.finishedMatch("560602", WEEK, EVERTON, LIVERPOOL,
            1, 0);

    @Test
    void sumaLasMetricasDeTodosLosPartidosDeLaSemana() {
        var weekStats = List.of(
                stats().inMatch(EVERTON_MAN_UTD).minutes(90).goals(1).shots(3).tackles(2).yellowCards(1).build(),
                stats().inMatch(EVERTON_LIVERPOOL).minutes(80).goals(2).shots(1).foulsCommitted(2).build());

        var performance = WeeklyPerformance.of(NDIAYE, weekStats);

        assertThat(performance.value(ValuationMetric.GOALS)).hasValueSatisfying(isNumber("3"));
        assertThat(performance.value(ValuationMetric.SHOTS)).hasValueSatisfying(isNumber("4"));
        assertThat(performance.value(ValuationMetric.TACKLES)).hasValueSatisfying(isNumber("2"));
        assertThat(performance.value(ValuationMetric.YELLOW_CARDS)).hasValueSatisfying(isNumber("1"));
        assertThat(performance.value(ValuationMetric.FOULS_COMMITTED)).hasValueSatisfying(isNumber("2"));
        assertThat(performance.value(ValuationMetric.MINUTES_PLAYED)).hasValueSatisfying(isNumber("170"));
        assertThat(performance.value(ValuationMetric.RED_CARDS)).hasValueSatisfying(isNumber("0"));
    }

    @Test
    void unJugadorSinEstadisticasTieneLasQuinceDeSumaEnCeroYNingunaDeLasQueRequierenJugar() {
        var performance = WeeklyPerformance.of(NDIAYE, List.of());

        var summed = EnumSet.complementOf(REQUIRE_PLAYING);
        assertThat(summed).hasSize(15);
        assertThat(performance.values()).containsOnlyKeys(summed);
        summed.forEach(metric -> assertThat(performance.value(metric)).hasValueSatisfying(isNumber("0")));
    }

    @Test
    void elRatingSePonderaPorMinutosYExcluyeLosPartidosSinRating() {
        var other = QuoteFixtures.finishedMatch("560603", WEEK, LIVERPOOL, EVERTON, 1, 1);
        var weekStats = List.of(
                stats().inMatch(EVERTON_MAN_UTD).minutes(90).rating("7.00").build(),
                stats().inMatch(EVERTON_LIVERPOOL).minutes(30).rating("9.00").build(),
                stats().inMatch(other).minutes(60).build());

        var performance = WeeklyPerformance.of(NDIAYE, weekStats);

        assertThat(performance.value(ValuationMetric.RATING)).contains(new BigDecimal("7.500000"));
        assertThat(performance.value(ValuationMetric.MINUTES_PLAYED)).hasValueSatisfying(isNumber("180"));
    }

    @Test
    void sinNingunPartidoConRatingElRatingNoSePuedeCalcular() {
        var performance = WeeklyPerformance.of(NDIAYE, List.of(stats().minutes(90).goals(1).build()));

        assertThat(performance.value(ValuationMetric.RATING)).isEmpty();
    }

    @Test
    void laPrecisionDePaseEsLosPrecisosSobreElTotalDeLaSemana() {
        var weekStats = List.of(
                stats().inMatch(EVERTON_MAN_UTD).minutes(90).passes(30, 24).build(),
                stats().inMatch(EVERTON_LIVERPOOL).minutes(90).passes(10, 6).build());

        var performance = WeeklyPerformance.of(NDIAYE, weekStats);

        assertThat(performance.value(ValuationMetric.PASS_ACCURACY)).contains(new BigDecimal("0.750000"));
        assertThat(performance.value(ValuationMetric.PASSES)).hasValueSatisfying(isNumber("40"));
    }

    @Test
    void conCeroPasesLaPrecisionNoSePuedeCalcular() {
        var performance = WeeklyPerformance.of(NDIAYE, List.of(stats().minutes(90).build()));

        assertThat(performance.value(ValuationMetric.PASS_ACCURACY)).isEmpty();
        assertThat(performance.value(ValuationMetric.PASSES)).hasValueSatisfying(isNumber("0"));
    }

    @Test
    void siSuEquipoNoEsNingunoDelPartidoNoHayResultadoNiVallaInvicta() {
        var performance = WeeklyPerformance.of(NDIAYE,
                List.of(stats().inMatch(ARSENAL_LIVERPOOL).minutes(90).rating("6.50").build()));

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).isEmpty();
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).isEmpty();
        assertThat(performance.value(ValuationMetric.RATING)).contains(new BigDecimal("6.500000"));
    }

    @Test
    void unaVictoriaValeUnoYCuentaLaVallaInvictaSoloSiElRivalNoConvirtio() {
        var weekStats = List.of(
                stats().inMatch(EVERTON_MAN_UTD).minutes(90).build(),
                stats().inMatch(EVERTON_LIVERPOOL).minutes(90).build());

        var performance = WeeklyPerformance.of(NDIAYE, weekStats);

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).contains(new BigDecimal("1.000000"));
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).hasValueSatisfying(isNumber("1"));
    }

    @Test
    void unEmpateSinGolesValeUnMedioYEsVallaInvicta() {
        var performance = WeeklyPerformance.of(SAKA,
                List.of(stats().forPlayer(SAKA).inMatch(ARSENAL_LIVERPOOL).minutes(90).build()));

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).contains(new BigDecimal("0.500000"));
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).hasValueSatisfying(isNumber("1"));
    }

    @Test
    void unaDerrotaComoVisitanteValeCeroYNoEsVallaInvicta() {
        var performance = WeeklyPerformance.of(ZIRKZEE,
                List.of(stats().forPlayer(ZIRKZEE).inMatch(EVERTON_MAN_UTD).minutes(90).build()));

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).contains(new BigDecimal("0.000000"));
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).hasValueSatisfying(isNumber("0"));
    }

    @Test
    void elResultadoPromediaSoloLosPartidosResolubles() {
        var manUtdEverton = QuoteFixtures.finishedMatch("560604", WEEK, MAN_UTD, EVERTON, 1, 1);
        var weekStats = List.of(
                stats().inMatch(EVERTON_MAN_UTD).minutes(90).build(),
                stats().inMatch(manUtdEverton).minutes(90).build(),
                stats().inMatch(ARSENAL_LIVERPOOL).minutes(90).build());

        var performance = WeeklyPerformance.of(NDIAYE, weekStats);

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).contains(new BigDecimal("0.750000"));
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).hasValueSatisfying(isNumber("0"));
    }

    @Test
    void unPartidoSinResultadoNoAportaAlResultadoNiALaVallaInvicta() {
        var unplayed = QuoteFixtures.scheduledMatch("560605", WEEK, EVERTON, MAN_UTD);

        var performance = WeeklyPerformance.of(NDIAYE, List.of(stats().inMatch(unplayed).minutes(90).build()));

        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).isEmpty();
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).isEmpty();
    }

    @Test
    void unSuplenteConCeroMinutosNoJugoAunqueSumeSusMetricas() {
        var performance = WeeklyPerformance.of(NDIAYE,
                List.of(stats().inMatch(EVERTON_MAN_UTD).minutes(0).rating("6.00").yellowCards(1).build()));

        assertThat(performance.value(ValuationMetric.RATING)).isEmpty();
        assertThat(performance.value(ValuationMetric.TEAM_RESULT)).isEmpty();
        assertThat(performance.value(ValuationMetric.CLEAN_SHEETS)).isEmpty();
        assertThat(performance.value(ValuationMetric.YELLOW_CARDS)).hasValueSatisfying(isNumber("1"));
        assertThat(performance.value(ValuationMetric.MINUTES_PLAYED)).hasValueSatisfying(isNumber("0"));
    }

    @Test
    void losValoresNoSePuedenModificar() {
        var performance = WeeklyPerformance.of(NDIAYE, List.of());

        assertThat(performance.values()).isUnmodifiable();
    }

    private static Consumer<BigDecimal> isNumber(String expected) {
        return value -> assertThat(value).isEqualByComparingTo(expected);
    }
}
