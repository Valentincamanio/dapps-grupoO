package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotingPlan;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * Orquesta una corrida del recálculo (research D17). No es transaccional, como {@code SyncService}:
 * cada semana es su propia transacción en {@link QuoteWeekWriter}.
 *
 * <p>Lee la estrategia activa una sola vez, al principio, y la usa toda la corrida aunque mientras
 * tanto cambie la activa (FR-039). Cotiza las semanas en orden; si una no se puede guardar, corta
 * ahí y no intenta las siguientes (no se saltea ninguna). Trabaja solo con lo guardado: no consulta
 * ninguna fuente externa.</p>
 *
 * <p>El caché se vacía después de soltar el candado, con las semanas ya confirmadas, y solo si se
 * cotizó alguna (research D21).</p>
 */
@Service
@RequiredArgsConstructor
public class QuoteRecalculationService {

    private final RecalculationLock recalculationLock;
    private final StrategyRepository strategyRepository;
    private final QuoteRepository quoteRepository;
    private final MatchRepository matchRepository;
    private final QuoteWeekWriter weekWriter;
    private final RecalculationReportLogger reportLogger;
    private final QuoteCacheInvalidator cacheInvalidator;
    private final Clock clock;

    public RecalculationReport recalculate(RecalculationOrigin origin) {
        RecalculationReport report = recalculationLock.runExclusively(() -> run(origin));
        if (!report.quotedWeeks().isEmpty()) {
            cacheInvalidator.invalidateAll();
        }
        return report;
    }

    /**
     * Si queda alguna semana cerrada sin cotizar, según lo guardado. Solo lee: no toma candados ni
     * escribe. Con esto el arranque decide si dispara el recálculo (US6.2, US6.3).
     */
    public boolean hasClosedUnquotedWeeks() {
        return plan(clock.instant()).hasClosedUnquotedWeeks();
    }

    private RecalculationReport run(RecalculationOrigin origin) {
        StrategyVersion strategy = strategyRepository.findCatalog().activeVersion();
        var startedAt = clock.instant();
        reportLogger.started(origin, strategy.ref());
        var plan = plan(startedAt);
        var report = RecalculationReport.builder(origin, startedAt, strategy.ref())
                .pendingWeeks(plan.pendingWeeks());
        quoteInOrder(plan.weeksToQuote(), strategy, report);
        RecalculationReport finished = report.build(clock.instant());
        reportLogger.log(finished);
        return finished;
    }

    private QuotingPlan plan(Instant now) {
        var finishedMatches = matchRepository.findAllFinished();
        return QuotingPlan.of(quoteRepository.findLastCalculatedWeek(),
                QuotingPlan.firstWeekWithStats(finishedMatches), finishedMatches, now);
    }

    private void quoteInOrder(List<QuoteWeek> weeks, StrategyVersion strategy, RecalculationReport.Builder report) {
        for (QuoteWeek week : weeks) {
            try {
                report.quotedWeek(weekWriter.quoteWeek(week, strategy));
            } catch (DataAccessException e) {
                reportLogger.weekFailed(week, e);
                report.interruptedAt(week);
                return;
            }
        }
    }
}
