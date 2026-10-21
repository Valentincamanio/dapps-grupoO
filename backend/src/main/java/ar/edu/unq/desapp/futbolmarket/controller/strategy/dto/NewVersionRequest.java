package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

/**
 * Cuerpo de {@code POST /strategies/{id}/versions}: los parámetros de la versión nueva. Factor y
 * mínimo omitidos conservan los de la versión anterior; los pesos se reemplazan por completo.
 *
 * <p>La opción {@code requote} se suma en US5; hasta entonces se ignora, como cualquier propiedad
 * desconocida.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewVersionRequest(BigDecimal scaleFactor, BigDecimal minimumValue,
                                Map<String, BigDecimal> defaultWeights,
                                Map<Position, Map<String, BigDecimal>> positionWeights) {

    public StrategyParametersRequest parameters() {
        return new StrategyParametersRequest(scaleFactor, minimumValue, defaultWeights, positionWeights);
    }
}
