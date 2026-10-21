package ar.edu.unq.desapp.futbolmarket.service.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.config.QuoteRecalculationProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class QuoteStartupRecalculationTest {

    private static final String SKIPPED = "Se omite el recálculo de arranque";

    @Mock
    private QuoteRecalculationService recalculationService;

    @Test
    void conSemanasPendientesDisparaElRecalculoConOrigenDeArranque() {
        when(recalculationService.hasClosedUnquotedWeeks()).thenReturn(true);
        QuoteStartupRecalculation startup = startup(true);

        startup.recalculatePendingWeeks();

        verify(recalculationService).recalculate(RecalculationOrigin.STARTUP);
    }

    @Test
    void sinSemanasPendientesNoDisparaNada() {
        when(recalculationService.hasClosedUnquotedWeeks()).thenReturn(false);
        QuoteStartupRecalculation startup = startup(true);

        startup.recalculatePendingWeeks();

        verify(recalculationService, never()).recalculate(any());
    }

    @Test
    void conElRecalculoDeArranqueApagadoNiSiquieraMiraLasSemanas() {
        QuoteStartupRecalculation startup = startup(false);

        startup.recalculatePendingWeeks();

        verifyNoInteractions(recalculationService);
    }

    @Test
    void conUnaSincronizacionEnCursoLoOmiteConUnWarnSinPropagarLaExcepcion(CapturedOutput output) {
        when(recalculationService.hasClosedUnquotedWeeks()).thenReturn(true);
        when(recalculationService.recalculate(RecalculationOrigin.STARTUP))
                .thenThrow(new RecalculationBlockedBySyncException());
        QuoteStartupRecalculation startup = startup(true);

        assertThatCode(startup::recalculatePendingWeeks).doesNotThrowAnyException();
        assertLine(output, SKIPPED)
                .contains("WARN")
                .contains("No se puede recalcular: hay una sincronización en curso.")
                .contains("Las semanas pendientes quedan para el recálculo semanal.");
    }

    @Test
    void conOtroRecalculoEnCursoLoOmiteConUnWarnSinPropagarLaExcepcion(CapturedOutput output) {
        when(recalculationService.hasClosedUnquotedWeeks()).thenReturn(true);
        when(recalculationService.recalculate(RecalculationOrigin.STARTUP))
                .thenThrow(new RecalculationInProgressException());
        QuoteStartupRecalculation startup = startup(true);

        assertThatCode(startup::recalculatePendingWeeks).doesNotThrowAnyException();
        assertLine(output, SKIPPED)
                .contains("WARN")
                .contains("Ya hay un recálculo en curso.");
    }

    private QuoteStartupRecalculation startup(boolean onStartup) {
        QuoteRecalculationProperties properties =
                new QuoteRecalculationProperties("-", "America/Argentina/Buenos_Aires", onStartup);
        return new QuoteStartupRecalculation(properties, recalculationService);
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
