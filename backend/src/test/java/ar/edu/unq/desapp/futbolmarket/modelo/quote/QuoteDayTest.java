package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.FutureQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidDateRangeException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteDayTest {
    private static final LocalDate AUGUST_20 = LocalDate.of(2026, 8, 20);

    @Test
    void parseaUnaFechaConFormatoAAAAMMDD() {
        LocalDate day = QuoteDay.parse("2026-08-20");

        assertThat(day).isEqualTo(AUGUST_20);
    }

    @Test
    void rechazaUnaFechaInexistenteConElValorEnElMensaje() {
        assertThatThrownBy(() -> QuoteDay.parse("2026-13-45"))
                .isInstanceOf(InvalidQuoteDateException.class)
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha '2026-13-45' no es válida: use el formato AAAA-MM-DD.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "20-08-2026", "2026/08/20", "2026-8-20", "", "hoy", "2026-08-20T00:00"})
    void rechazaCualquierTextoQueNoSeaUnDiaValidoAAAAMMDD(String value) {
        assertThatThrownBy(() -> QuoteDay.parse(value))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessageContaining("'" + value + "'");
    }

    @Test
    void sinFechaRespondeQueEsObligatoria() {
        assertThatThrownBy(() -> QuoteDay.parse(null))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessage("La fecha es obligatoria: use el formato AAAA-MM-DD.");
    }

    @Test
    void unaFechaFuturaSeRechazaConElValorEnElMensaje() {
        LocalDate future = LocalDate.of(2027, 1, 1);

        assertThatThrownBy(() -> QuoteDay.requireNotFuture(future, AUGUST_20))
                .isInstanceOf(FutureQuoteDateException.class)
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La fecha 2027-01-01 es futura.");
    }

    @Test
    void hoyYUnaFechaPasadaNoSonFuturas() {
        assertThat(QuoteDay.requireNotFuture(AUGUST_20, AUGUST_20)).isEqualTo(AUGUST_20);
        assertThat(QuoteDay.requireNotFuture(AUGUST_20.minusDays(1), AUGUST_20)).isEqualTo(AUGUST_20.minusDays(1));
    }

    @Test
    void unRangoInvertidoSeRechazaConLasDosFechasEnElMensaje() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> QuoteDay.requireOrdered(from, to))
                .isInstanceOf(InvalidDateRangeException.class)
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El inicio del rango (2026-09-01) es posterior al final (2026-08-01).");
    }

    @Test
    void unRangoDeUnSoloDiaEsValido() {
        assertThatCode(() -> QuoteDay.requireOrdered(AUGUST_20, AUGUST_20)).doesNotThrowAnyException();
    }

    /**
     * Argentina es UTC−3: el día arranca a las 03:00Z y termina a las 03:00Z del día siguiente.
     */
    @Test
    void elDiaVaDeLas0000ALas0000DelDiaSiguienteEnHoraDeArgentina() {
        Instant start = QuoteDay.startOf(AUGUST_20);
        Instant end = QuoteDay.endOf(AUGUST_20);

        assertThat(start).isEqualTo(Instant.parse("2026-08-20T03:00:00Z"));
        assertThat(end).isEqualTo(Instant.parse("2026-08-21T03:00:00Z"));
    }

    @Test
    void elFinDelDomingoEsElInicioDeLaSemanaSiguiente() {
        LocalDate sunday = LocalDate.of(2026, 8, 23);

        assertThat(QuoteDay.endOf(sunday)).isEqualTo(new QuoteWeek(LocalDate.of(2026, 8, 17)).end());
    }

    /**
     * A las 01:00Z del 21/08 todavía son las 22:00 del 20/08 en Argentina.
     */
    @Test
    void hoyEsElDiaEnHoraDeArgentinaYNoEnUTC() {
        LocalDate today = QuoteDay.today(Instant.parse("2026-08-21T01:00:00Z"));

        assertThat(today).isEqualTo(AUGUST_20);
    }
}
