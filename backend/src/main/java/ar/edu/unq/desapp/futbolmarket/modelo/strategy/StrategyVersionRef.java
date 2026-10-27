package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

/**
 * Referencia liviana a la versión con que se calculó una cotización: alcanza para informarla sin
 * cargar sus pesos.
 */
public record StrategyVersionRef(Long versionId, Long strategyId, String name, int version) {
}
