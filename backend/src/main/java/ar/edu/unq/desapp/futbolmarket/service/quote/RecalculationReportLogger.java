package ar.edu.unq.desapp.futbolmarket.service.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * Escribe en el registro un recálculo (research D17): la línea de inicio, una por semana cotizada,
 * una con las pendientes y su motivo, y la línea final con la duración y la estrategia. Una semana
 * que no se pudo guardar va en {@code WARN}.
 *
 * <p>Solo traduce a texto lo que decidió el modelo. No hay datos de usuarios en un recálculo.</p>
 */
@Component
public class RecalculationReportLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecalculationReportLogger.class);
    private static final String SEPARATOR = ", ";

    public void started(RecalculationOrigin origin, StrategyVersionRef strategy) {
        String strategyName = describe(strategy);
        LOGGER.info("Recálculo de cotizaciones ({}) iniciado con la estrategia {}.", origin, strategyName);
    }

    /**
     * La semana que no se pudo guardar: de ella no quedó nada y las siguientes no se intentan.
     */
    public void weekFailed(QuoteWeek week, Exception cause) {
        LOGGER.warn("Semana {}: no se pudo guardar. No quedó ninguna cotización de esa semana y las siguientes "
                + "no se intentaron.", week.monday(), cause);
    }

    public void log(RecalculationReport report) {
        report.quotedWeeks().forEach(RecalculationReportLogger::logWeek);
        logPending(report);
        String summary = summary(report);
        LOGGER.info(summary);
    }

    private static void logWeek(WeekResult result) {
        LOGGER.info("Semana {} cotizada: {} iniciales, {} calculadas, {} recotizadas.", result.week().monday(),
                result.initial(), result.calculated(), result.requoted());
    }

    /**
     * La línea de pendientes se escribe solo si hay alguna.
     */
    private static void logPending(RecalculationReport report) {
        if (report.pendingWeeks().isEmpty()) {
            return;
        }
        String pending = String.join(SEPARATOR,
                report.pendingWeeks().stream().map(RecalculationReportLogger::describePending).toList());
        LOGGER.info("Semanas pendientes: {}", pending);
    }

    private static String summary(RecalculationReport report) {
        String result = report.isEmpty()
                ? "no había semanas para cotizar"
                : "%d semanas cotizadas, %d pendientes".formatted(report.quotedWeeks().size(),
                        report.pendingWeeks().size());
        String interruption = report.interruptedAt()
                .map(week -> " Se interrumpió en la semana %s.".formatted(week.monday()))
                .orElse("");
        return "Recálculo de cotizaciones (%s) terminado en %d s con la estrategia %s: %s.%s".formatted(
                report.origin(), report.duration().toSeconds(), describe(report.strategy()), result, interruption);
    }

    private static String describePending(PendingWeek pending) {
        return "%s (%s)".formatted(pending.week().monday(), pending.reason());
    }

    private static String describe(StrategyVersionRef strategy) {
        return "'%s' v%d".formatted(strategy.name(), strategy.version());
    }
}
