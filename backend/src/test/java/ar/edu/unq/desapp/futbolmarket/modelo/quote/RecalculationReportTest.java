package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_A;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecalculationReportTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-07T09:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(42);

    @Test
    void elBuilderAcumulaLasSemanasYLasPendientesEnOrden() {
        var first = new WeekResult(WEEK, 2600, 2600, 0);
        var second = new WeekResult(WEEK.next(), 3, 2603, 0);
        var pending = List.of(new PendingWeek(WEEK.next().next(), PendingReason.MISSING_STATS));

        var report = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A.ref())
                .quotedWeek(first)
                .quotedWeek(second)
                .pendingWeeks(pending)
                .build(FINISHED_AT);

        assertThat(report.origin()).isEqualTo(RecalculationOrigin.MANUAL);
        assertThat(report.strategy()).isEqualTo(STRATEGY_A.ref());
        assertThat(report.quotedWeeks()).containsExactly(first, second);
        assertThat(report.pendingWeeks()).isEqualTo(pending);
        assertThat(report.duration()).isEqualTo(Duration.ofSeconds(42));
        assertThat(report.isEmpty()).isFalse();
        assertThat(report.interruptedAt()).isEmpty();
        assertThat(report.wasInterrupted()).isFalse();
    }

    @Test
    void sinSemanasParaCotizarElInformeEstaVacioAunqueHayaPendientes() {
        var report = RecalculationReport.builder(RecalculationOrigin.WEEKLY, STARTED_AT, STRATEGY_A.ref())
                .pendingWeeks(List.of(new PendingWeek(WEEK, PendingReason.MISSING_STATS)))
                .build(FINISHED_AT);

        assertThat(report.isEmpty()).isTrue();
        assertThat(report.quotedWeeks()).isEmpty();
    }

    @Test
    void unaSemanaQueFallaQuedaComoInterrupcionYElInformeNoEstaVacio() {
        var report = RecalculationReport.builder(RecalculationOrigin.STARTUP, STARTED_AT, STRATEGY_A.ref())
                .quotedWeek(new WeekResult(WEEK, 10, 10, 0))
                .interruptedAt(WEEK.next())
                .build(FINISHED_AT);

        assertThat(report.interruptedAt()).contains(WEEK.next());
        assertThat(report.wasInterrupted()).isTrue();
        assertThat(report.isEmpty()).isFalse();
    }

    @Test
    void siFallaLaPrimeraSemanaTampocoEstaVacio() {
        var report = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A.ref())
                .interruptedAt(WEEK)
                .build(FINISHED_AT);

        assertThat(report.quotedWeeks()).isEmpty();
        assertThat(report.isEmpty()).isFalse();
    }

    @Test
    void unaCorridaSeInterrumpeUnaSolaVez() {
        var builder = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A.ref())
                .interruptedAt(WEEK);

        assertThatThrownBy(() -> builder.interruptedAt(WEEK.next()))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void lasListasDelInformeNoSePuedenModificar() {
        var report = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A.ref())
                .quotedWeek(new WeekResult(WEEK, 1, 1, 0))
                .build(FINISHED_AT);

        assertThat(report.quotedWeeks()).isUnmodifiable();
        assertThat(report.pendingWeeks()).isUnmodifiable();
    }

    @Test
    void elInformeExigeLaEstrategia() {
        var builder = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, null);

        assertThatThrownBy(() -> builder.build(FINISHED_AT)).isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void elResultadoDeUnaSemanaCuentaLasCotizacionesPorTipo() {
        var quotes = List.of(
                QuoteFixtures.initial(1L, WEEK, STRATEGY_A),
                QuoteFixtures.calculated(1L, WEEK, "103.00", "100.00", "0.300000", STRATEGY_A),
                QuoteFixtures.calculated(2L, WEEK, "99.00", "100.00", "-0.100000", STRATEGY_A));

        var result = WeekResult.of(WEEK, quotes);

        assertThat(result).isEqualTo(new WeekResult(WEEK, 1, 2, 0));
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void unaCantidadNegativaEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> new WeekResult(WEEK, -1, 0, 0)).isInstanceOf(QuoteInvariantException.class);
    }
}
