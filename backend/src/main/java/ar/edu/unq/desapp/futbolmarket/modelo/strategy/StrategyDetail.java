package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

/**
 * Una estrategia con todas sus versiones y si es la activa, como la devuelve la administración de
 * estrategias. Si es la activa, su versión activa es la última (D13).
 */
public record StrategyDetail(Strategy strategy, boolean active) {

    public static StrategyDetail of(StrategyCatalog catalog, Strategy strategy) {
        return new StrategyDetail(strategy, catalog.isActive(strategy.id()));
    }

    /**
     * La estrategia con ese id en el catálogo; si no existe, {@code StrategyNotFoundException}.
     */
    public static StrategyDetail of(StrategyCatalog catalog, Long strategyId) {
        return of(catalog, catalog.find(strategyId));
    }
}
