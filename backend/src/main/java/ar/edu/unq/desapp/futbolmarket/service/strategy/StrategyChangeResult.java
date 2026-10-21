package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;

/**
 * Lo que deja editar o activar una estrategia: la estrategia después del cambio y, si se recotizó
 * en el acto, el informe de la recotización. Vacío en cualquier otro caso (la semana siguiente, o
 * una opción que se ignoró).
 */
public record StrategyChangeResult(StrategyDetail strategy, Optional<RecalculationReport> requotation) {
    public StrategyChangeResult {
        requotation = requotation == null ? Optional.empty() : requotation;
    }

    static StrategyChangeResult withoutRequotation(StrategyDetail strategy) {
        return new StrategyChangeResult(strategy, Optional.empty());
    }
}
