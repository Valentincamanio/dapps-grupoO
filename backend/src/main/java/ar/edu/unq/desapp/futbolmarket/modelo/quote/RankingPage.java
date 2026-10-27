package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * Una hoja del ranking y la versión de la estrategia activa al consultarla (FR-045). Las filas
 * pueden haberse calculado con otra versión: cada cotización informa la suya.
 */
public record RankingPage(QuotePage<RankedQuote> page, StrategyVersionRef activeStrategy) {
}
