package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;

/**
 * El soporte de los tests de cotización arma datos válidos para la 006: si su constructor cambia,
 * este test es el primero en fallar.
 */
class PlayerMatchStatsBuilderTest {

    @Test
    void sinIndicarNadaArmaUnRendimientoConTodoEnCeroYSinRating() {
        PlayerMatchStats built = stats().build();

        assertThat(built.player()).isEqualTo(QuoteFixtures.NDIAYE);
        assertThat(built.match()).isEqualTo(QuoteFixtures.EVERTON_MAN_UTD);
        assertThat(built.minutesPlayed()).isZero();
        assertThat(built.rating()).isNull();
        assertThat(Arrays.stream(Metric.values()).map(metric -> built.counts().get(metric))).containsOnly(0);
    }

    @Test
    void tomaLasMetricasLosMinutosYElRatingIndicados() {
        PlayerMatchStats built = stats()
                .forPlayer(QuoteFixtures.SAKA)
                .inMatch(QuoteFixtures.ARSENAL_LIVERPOOL)
                .minutes(90)
                .goals(2)
                .tackles(3)
                .passes(40, 35)
                .rating("7.2")
                .build();

        assertThat(built.player()).isEqualTo(QuoteFixtures.SAKA);
        assertThat(built.minutesPlayed()).isEqualTo(90);
        assertThat(built.counts().get(Metric.GOALS)).isEqualTo(2);
        assertThat(built.counts().get(Metric.TACKLES)).isEqualTo(3);
        assertThat(built.counts().get(Metric.PASSES)).isEqualTo(40);
        assertThat(built.counts().get(Metric.ACCURATE_PASSES)).isEqualTo(35);
        assertThat(built.rating()).isEqualTo(new BigDecimal("7.20"));
    }

    @Test
    void losPartidosDeLosFixturesCaenEnLaSemanaDeReferenciaYTienenGanador() {
        assertThat(QuoteWeek.containing(QuoteFixtures.EVERTON_MAN_UTD.utcDate())).isEqualTo(QuoteFixtures.WEEK);
        assertThat(QuoteFixtures.EVERTON_MAN_UTD.winner()).isEqualTo(MatchWinner.HOME_TEAM);
        assertThat(QuoteFixtures.ARSENAL_LIVERPOOL.winner()).isEqualTo(MatchWinner.DRAW);
        assertThat(QuoteFixtures.EVERTON_MAN_UTD.needsStats()).isFalse();
        assertThat(QuoteFixtures.finishedMatchWithoutStats("1", QuoteFixtures.WEEK, QuoteFixtures.EVERTON,
                QuoteFixtures.MAN_UTD).needsStats()).isTrue();
    }

    @Test
    void lasCotizacionesDeEjemploRespetanSusInvariantes() {
        Quote initial = QuoteFixtures.initial(QuoteFixtures.NDIAYE.id(), QuoteFixtures.WEEK, QuoteFixtures.STRATEGY_A);
        Quote calculated = QuoteFixtures.calculated(QuoteFixtures.NDIAYE.id(), QuoteFixtures.WEEK, "103.00",
                "100.00", "0.300000", QuoteFixtures.STRATEGY_A);

        assertThat(initial.validFrom()).isEqualTo(QuoteFixtures.WEEK.start());
        assertThat(calculated.validFrom()).isEqualTo(QuoteFixtures.WEEK.end());
        assertThat(calculated.strategy().name()).isEqualTo(QuoteFixtures.STRATEGY_A.name());
    }
}
