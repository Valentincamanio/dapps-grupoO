package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteWeekTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);
    private static final QuoteWeek WEEK = new QuoteWeek(MONDAY);

    @Test
    void unPartidoDelDomingoALas2359DeArgentinaEsDeEsaSemana() {
        Instant sundayNight = argentina(LocalDateTime.of(2026, 8, 23, 23, 59));

        QuoteWeek week = QuoteWeek.containing(sundayNight);

        assertThat(week).isEqualTo(WEEK);
    }

    @Test
    void unPartidoDelLunesALas0000DeArgentinaEsDeLaSemanaSiguiente() {
        Instant mondayMidnight = argentina(LocalDateTime.of(2026, 8, 24, 0, 0));

        QuoteWeek week = QuoteWeek.containing(mondayMidnight);

        assertThat(week).isEqualTo(WEEK.next());
    }

    /**
     * El domingo 23:30 de Argentina ya es lunes en UTC: la semana sale de la hora local.
     */
    @Test
    void unPartidoQueEnUtcYaEsLunesSigueSiendoDeLaSemanaDelDomingo() {
        Instant mondayInUtc = Instant.parse("2026-08-24T02:30:00Z");

        QuoteWeek week = QuoteWeek.containing(mondayInUtc);

        assertThat(week).isEqualTo(WEEK);
    }

    @Test
    void laSemanaEmpiezaElLunesALas0300ZYTerminaElLunesSiguiente() {
        assertThat(WEEK.start()).isEqualTo(Instant.parse("2026-08-17T03:00:00Z"));
        assertThat(WEEK.end()).isEqualTo(Instant.parse("2026-08-24T03:00:00Z"));
    }

    @Test
    void laSemanaEnCursoNoEstaCerradaYAlLlegarSuCierreSi() {
        Instant beforeClosing = WEEK.end().minusNanos(1);

        assertThat(WEEK.isClosedAt(beforeClosing)).isFalse();
        assertThat(WEEK.isClosedAt(WEEK.end())).isTrue();
    }

    @Test
    void laUltimaCerradaUnLunesALas0000ExactasEsLaQueTerminaEnEseInstante() {
        Instant mondayMidnight = WEEK.end();

        QuoteWeek lastClosed = QuoteWeek.lastClosedAt(mondayMidnight);

        assertThat(lastClosed).isEqualTo(WEEK);
        assertThat(lastClosed.isClosedAt(mondayMidnight)).isTrue();
    }

    @Test
    void laUltimaCerradaAMitadDeSemanaEsLaAnterior() {
        Instant wednesday = argentina(LocalDateTime.of(2026, 8, 26, 12, 0));

        QuoteWeek lastClosed = QuoteWeek.lastClosedAt(wednesday);

        assertThat(lastClosed).isEqualTo(WEEK);
    }

    @Test
    void siguienteYAnteriorSonLasSemanasContiguas() {
        assertThat(WEEK.next().monday()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(WEEK.previous().monday()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(WEEK.next().previous()).isEqualTo(WEEK);
    }

    @Test
    void lasSemanasSeOrdenanCronologicamente() {
        List<QuoteWeek> weeks = new ArrayList<>(List.of(WEEK.next(), WEEK.previous(), WEEK));

        weeks.sort(null);

        assertThat(weeks).containsExactly(WEEK.previous(), WEEK, WEEK.next());
    }

    @Test
    void rechazaUnDiaQueNoEsLunes() {
        LocalDate tuesday = MONDAY.plusDays(1);

        assertThatThrownBy(() -> new QuoteWeek(tuesday))
                .isInstanceOf(QuoteInvariantException.class)
                .hasMessageContaining("2026-08-18");
    }

    @Test
    void rechazaUnaSemanaSinLunes() {
        assertThatThrownBy(() -> new QuoteWeek(null)).isInstanceOf(QuoteInvariantException.class);
    }

    private static Instant argentina(LocalDateTime localTime) {
        return localTime.atZone(QuoteWeek.ZONE).toInstant();
    }
}
