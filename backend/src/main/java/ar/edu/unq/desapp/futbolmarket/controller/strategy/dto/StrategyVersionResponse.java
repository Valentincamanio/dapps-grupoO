package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;

/**
 * Una versión de una estrategia (esquema {@code StrategyVersion} del contrato). Los pesos van por
 * nombre de métrica, en el orden de {@code ValuationMetric}, y solo los mayores que 0; una posición
 * sin juego propio no figura en {@code positionWeights}.
 */
public record StrategyVersionResponse(int version, boolean active, BigDecimal scaleFactor,
                                      BigDecimal minimumValue, Map<String, BigDecimal> defaultWeights,
                                      Map<Position, Map<String, BigDecimal>> positionWeights,
                                      Instant createdAt) {

    public static StrategyVersionResponse from(StrategyVersion version, boolean active) {
        var positionWeights = new LinkedHashMap<Position, Map<String, BigDecimal>>();
        version.positionWeights().forEach((position, weights) -> positionWeights.put(position, byName(weights)));
        return new StrategyVersionResponse(version.version(), active, version.scaleFactor(), version.minimumValue(),
                byName(version.defaultWeights()), positionWeights, version.createdAt());
    }

    private static Map<String, BigDecimal> byName(WeightSet weights) {
        var byName = new LinkedHashMap<String, BigDecimal>();
        weights.weights().forEach((metric, weight) -> byName.put(metric.name(), weight));
        return byName;
    }
}
