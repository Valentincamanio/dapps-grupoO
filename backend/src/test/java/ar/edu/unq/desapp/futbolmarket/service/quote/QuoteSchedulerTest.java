package ar.edu.unq.desapp.futbolmarket.service.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class QuoteSchedulerTest {

    private static final String SKIPPED = "Se omite el recálculo semanal";

    @Mock
    private QuoteRecalculationService recalculationService;

    @Test
    void disparaElRecalculoConOrigenSemanal() {
        QuoteScheduler scheduler = scheduler();

        scheduler.runWeeklyRecalculation();

        verify(recalculationService).recalculate(RecalculationOrigin.WEEKLY);
    }

    @Test
    void conOtroRecalculoEnCursoNoPropagaLaExcepcionYDejaUnWarnConElMotivo(CapturedOutput output) {
        when(recalculationService.recalculate(RecalculationOrigin.WEEKLY))
                .thenThrow(new RecalculationInProgressException());
        QuoteScheduler scheduler = scheduler();

        assertThatCode(scheduler::runWeeklyRecalculation).doesNotThrowAnyException();
        assertLine(output, SKIPPED)
                .contains("WARN")
                .contains("Ya hay un recálculo en curso.");
    }

    @Test
    void conUnaSincronizacionEnCursoNoPropagaLaExcepcionYDejaUnWarnConElMotivo(CapturedOutput output) {
        when(recalculationService.recalculate(RecalculationOrigin.WEEKLY))
                .thenThrow(new RecalculationBlockedBySyncException());
        QuoteScheduler scheduler = scheduler();

        assertThatCode(scheduler::runWeeklyRecalculation).doesNotThrowAnyException();
        assertLine(output, SKIPPED)
                .contains("WARN")
                .contains("No se puede recalcular: hay una sincronización en curso.");
    }

    @Test
    void laCorridaSemanalTomaElCronYLaZonaDeLaConfiguracion() throws NoSuchMethodException {
        Scheduled scheduled = QuoteScheduler.class.getMethod("runWeeklyRecalculation").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${futbolmarket.quotes.recalculation.cron}");
        assertThat(scheduled.zone()).isEqualTo("${futbolmarket.quotes.recalculation.zone}");
    }

    private QuoteScheduler scheduler() {
        return new QuoteScheduler(recalculationService);
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
