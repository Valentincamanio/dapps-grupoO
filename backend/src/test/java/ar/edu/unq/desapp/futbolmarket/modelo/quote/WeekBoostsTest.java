package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.ClosedBoostWeekException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidBoostException;

/**
 * Las reglas de los potenciados de una semana. La semana es la del lunes 02/11/2026.
 */
class WeekBoostsTest {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 11, 2));
    private static final List<PlayerBoost> FOUR = List.of(
            new PlayerBoost(1L, 300), new PlayerBoost(2L, 200), new PlayerBoost(3L, 250), new PlayerBoost(4L, 400));

    @Test
    void cuatroJugadoresDistintosOSinNingunoSonValidos() {
        var four = new WeekBoosts(WEEK, FOUR);
        var none = WeekBoosts.none(WEEK);

        var fourIds = four.playerIds();

        assertThat(fourIds).containsExactly(1L, 2L, 3L, 4L);
        assertThat(four.isEmpty()).isFalse();
        assertThat(none.isEmpty()).isTrue();
    }

    @Test
    void otraCantidadQueCuatroSeRechaza() {
        var three = FOUR.subList(0, 3);

        assertThatThrownBy(() -> new WeekBoosts(WEEK, three))
                .isInstanceOf(InvalidBoostException.class)
                .hasMessage("Una semana lleva exactamente 4 potenciados, o ninguno, y se eligieron 3.");
    }

    @Test
    void unJugadorRepetidoSeRechaza() {
        var repeated = List.of(new PlayerBoost(1L, 300), new PlayerBoost(2L, 200), new PlayerBoost(1L, 250),
                new PlayerBoost(4L, 400));

        assertThatThrownBy(() -> new WeekBoosts(WEEK, repeated))
                .isInstanceOf(InvalidBoostException.class)
                .hasMessage("El jugador 1 está repetido entre los potenciados.");
    }

    @Test
    void elMultiplicadorEsElDelPorcentajeYUnoParaLosNoPotenciados() {
        var boosts = new WeekBoosts(WEEK, FOUR);

        var boosted = boosts.multiplierFor(3L);
        var notBoosted = boosts.multiplierFor(99L);

        assertThat(boosted).isEqualByComparingTo("2.5");
        assertThat(notBoosted).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void seEligenParaLaSemanaEnCursoYParaLasProximas() {
        Instant duringWeek = WEEK.start().plus(Duration.ofDays(3));
        Instant beforeWeek = WEEK.start().minus(Duration.ofDays(3));

        var current = WeekBoosts.choose(WEEK, FOUR, duringWeek);
        var upcoming = WeekBoosts.choose(WEEK, FOUR, beforeWeek);

        assertThat(current.statusAt(duringWeek)).isEqualTo(BoostWeekStatus.CURRENT);
        assertThat(upcoming.statusAt(beforeWeek)).isEqualTo(BoostWeekStatus.UPCOMING);
    }

    @Test
    void unaSemanaCerradaNoSeCambiaNiAlLunesALasCeroHoras() {
        Instant closing = WEEK.end();

        assertThatThrownBy(() -> WeekBoosts.choose(WEEK, FOUR, closing))
                .isInstanceOf(ClosedBoostWeekException.class)
                .hasMessage("La semana del 2026-11-02 ya cerró: sus potenciados no se pueden cambiar.");
        assertThat(WeekBoosts.none(WEEK).statusAt(closing)).isEqualTo(BoostWeekStatus.CLOSED);
    }
}
