package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * Una hoja de la simulación, con la versión simulada ({@code null} si fueron parámetros enviados),
 * cuántas semanas cerradas se reprodujeron y las que hoy quedarían pendientes (research D19).
 */
public record SimulationResult(QuotePage<SimulatedQuote> page, StrategyVersionRef strategy, int weeksSimulated,
                               List<PendingWeek> pendingWeeks) {
    public SimulationResult {
        pendingWeeks = List.copyOf(pendingWeeks);
    }
}
