package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyChangeConflictException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteWeekWriter;
import lombok.RequiredArgsConstructor;

/**
 * La parte transaccional de un cambio de estrategia (research D14), como {@code SyncWriteService}
 * en la 005: {@link StrategyChangeService} decide y este bean escribe.
 *
 * <p>{@link #apply(StrategyChange)} es el cambio "la semana siguiente": guarda la versión nueva y
 * mueve la marca {@code active} en una sola transacción, sin recotizar y sin tomar candados. Un
 * recálculo en curso termina con la estrategia que leyó al empezar (FR-039).</p>
 *
 * <p>{@link #applyAndRequote(StrategyChange, Instant)} es el cambio "ahora": guarda el cambio y
 * recotiza en la misma transacción, así que si cualquier paso falla la activa no cambia. Los
 * candados los toma {@link StrategyChangeService} afuera, para soltarlos después del commit.</p>
 */
@Service
@RequiredArgsConstructor
public class StrategyChangeWriter {

    private final StrategyRepository strategyRepository;
    private final QuoteWeekWriter weekWriter;
    private final Clock clock;

    @Transactional
    public StrategyCatalog apply(StrategyChange change) {
        return strategyRepository.save(change);
    }

    /**
     * Guarda el cambio y recotiza con la versión que queda activa (origen {@code STRATEGY_CHANGE}).
     * Un cambio vacío (activar la que ya es la activa) no escribe la estrategia, pero recotiza igual.
     * Si un índice único rechaza alguna fila, otro cambio se cruzó con este: responde 409 y no queda
     * nada.
     */
    @Transactional
    public RecalculationReport applyAndRequote(StrategyChange change, Instant now) {
        try {
            StrategyCatalog saved = change.isEmpty() ? change.catalog() : strategyRepository.save(change);
            StrategyVersion strategy = saved.activeVersion();
            var report = RecalculationReport.builder(RecalculationOrigin.STRATEGY_CHANGE, now, strategy.ref());
            weekWriter.requoteLastWeek(strategy, now, report);
            return report.build(clock.instant());
        } catch (DataIntegrityViolationException e) {
            throw new StrategyChangeConflictException();
        }
    }
}
