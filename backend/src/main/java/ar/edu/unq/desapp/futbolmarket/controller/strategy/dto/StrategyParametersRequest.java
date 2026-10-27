package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;

/**
 * Los parámetros de valuación (esquema {@code StrategyParameters} del contrato). Factor y mínimo
 * son opcionales; los pesos por defecto, obligatorios; los de cada posición, opcionales.
 *
 * <p>Los pesos llegan con el nombre de la métrica como texto y los valida el modelo
 * ({@link WeightSet#of}), así el 400 de una métrica desconocida repite su nombre (D13) y el de una
 * suma incorrecta dice de qué juego es.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrategyParametersRequest(BigDecimal scaleFactor, BigDecimal minimumValue,
                                        Map<String, BigDecimal> defaultWeights,
                                        Map<Position, Map<String, BigDecimal>> positionWeights) {

    public WeightSet defaultWeightSet() {
        return WeightSet.of(WeightSet.DEFAULT_LABEL, defaultWeights);
    }

    /**
     * Un juego por posición enviada; las demás usan el por defecto.
     */
    public Map<Position, WeightSet> positionWeightSets() {
        var sets = new EnumMap<Position, WeightSet>(Position.class);
        if (positionWeights != null) {
            positionWeights.forEach((position, weights) ->
                    sets.put(position, WeightSet.of(WeightSet.labelOf(position), weights)));
        }
        return Collections.unmodifiableMap(sets);
    }
}
