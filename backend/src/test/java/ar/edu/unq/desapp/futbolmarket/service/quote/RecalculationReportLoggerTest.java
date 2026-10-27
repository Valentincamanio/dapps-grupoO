package ar.edu.unq.desapp.futbolmarket.service.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingReason;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

@ExtendWith(OutputCaptureExtension.class)
class RecalculationReportLoggerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(80);
    private static final QuoteWeek FIRST = QuoteFixtures.WEEK;
    private static final QuoteWeek SECOND = FIRST.next();
    private static final StrategyVersionRef STRATEGY_A = QuoteFixtures.STRATEGY_A.ref();
    private static final String STRATEGY_A_TEXT = "con la estrategia 'Rendimiento de partido' v1";

    private final RecalculationReportLogger reportLogger = new RecalculationReportLogger();

    @Test
    void elInicioVaEnInfoConElOrigenYLaEstrategia(CapturedOutput output) {
        reportLogger.started(RecalculationOrigin.MANUAL, STRATEGY_A);

        assertLine(output, "iniciado")
                .contains("INFO")
                .contains("Recálculo de cotizaciones (MANUAL) iniciado " + STRATEGY_A_TEXT + ".");
    }

    @Test
    void unInformeConSemanasEscribeUnaLineaPorSemanaYLaFinalConDuracionYEstrategia(CapturedOutput output) {
        RecalculationReport report = RecalculationReport.builder(RecalculationOrigin.WEEKLY, STARTED_AT, STRATEGY_A)
                .quotedWeek(new WeekResult(FIRST, 11, 11, 0))
                .quotedWeek(new WeekResult(SECOND, 0, 11, 0))
                .build(FINISHED_AT);

        reportLogger.log(report);

        assertLine(output, "Semana 2026-08-17 cotizada")
                .contains("INFO")
                .contains("11 iniciales, 11 calculadas, 0 recotizadas.");
        assertLine(output, "Semana 2026-08-24 cotizada").contains("0 iniciales, 11 calculadas, 0 recotizadas.");
        assertLine(output, "terminado en")
                .contains("INFO")
                .contains("Recálculo de cotizaciones (WEEKLY) terminado en 80 s " + STRATEGY_A_TEXT
                        + ": 2 semanas cotizadas, 0 pendientes.");
        assertThat(output.getAll()).doesNotContain("Semanas pendientes");
    }

    @Test
    void lasPendientesVanEnUnaSolaLineaConSuMotivo(CapturedOutput output) {
        RecalculationReport report = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A)
                .quotedWeek(new WeekResult(FIRST, 11, 11, 0))
                .pendingWeeks(List.of(new PendingWeek(SECOND, PendingReason.MISSING_STATS),
                        new PendingWeek(SECOND.next(), PendingReason.PREVIOUS_WEEK_PENDING)))
                .build(FINISHED_AT);

        reportLogger.log(report);

        assertLine(output, "Semanas pendientes:")
                .contains("INFO")
                .contains("2026-08-24 (MISSING_STATS), 2026-08-31 (PREVIOUS_WEEK_PENDING)");
        assertLine(output, "terminado en").contains("1 semanas cotizadas, 2 pendientes.");
    }

    @Test
    void unInformeVacioDiceQueNoHabiaSemanasParaCotizar(CapturedOutput output) {
        RecalculationReport report = RecalculationReport.builder(RecalculationOrigin.STARTUP, STARTED_AT, STRATEGY_A)
                .build(STARTED_AT);

        reportLogger.log(report);

        assertLine(output, "terminado en")
                .contains("Recálculo de cotizaciones (STARTUP) terminado en 0 s " + STRATEGY_A_TEXT
                        + ": no había semanas para cotizar.");
        assertThat(output.getAll()).doesNotContain("cotizada:").doesNotContain("Semanas pendientes");
    }

    @Test
    void unaSemanaQueNoSePudoGuardarVaEnWarnYLaLineaFinalNombraLaInterrupcion(CapturedOutput output) {
        reportLogger.weekFailed(SECOND, new DataIntegrityViolationException("índice único"));
        RecalculationReport report = RecalculationReport.builder(RecalculationOrigin.MANUAL, STARTED_AT, STRATEGY_A)
                .quotedWeek(new WeekResult(FIRST, 11, 11, 0))
                .interruptedAt(SECOND)
                .build(FINISHED_AT);

        reportLogger.log(report);

        assertLine(output, "no se pudo guardar")
                .contains("WARN")
                .contains("Semana 2026-08-24: no se pudo guardar.");
        assertLine(output, "terminado en").contains("Se interrumpió en la semana 2026-08-24.");
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
