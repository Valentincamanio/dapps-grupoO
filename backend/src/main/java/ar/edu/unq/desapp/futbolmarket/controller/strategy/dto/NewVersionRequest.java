package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;

/**
 * Cuerpo de {@code POST /strategies/{id}/versions}: los parámetros de la versión nueva. Factor y
 * mínimo omitidos conservan los de la versión anterior; los pesos se reemplazan por completo.
 *
 * <p>{@code requote} ({@code AHORA} o {@code PROXIMA_SEMANA}) rige solo si la estrategia es la
 * activa; en una que no lo es se ignora. Un valor que no es ninguno de los dos es 400 igual.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewVersionRequest(BigDecimal scaleFactor, BigDecimal minimumValue,
                                Map<String, BigDecimal> defaultWeights,
                                Map<Position, Map<String, BigDecimal>> positionWeights,
                                String requote) {

    public StrategyParametersRequest parameters() {
        return new StrategyParametersRequest(scaleFactor, minimumValue, defaultWeights, positionWeights);
    }

    public RequoteTiming timing() {
        return RequoteTiming.fromWire(requote);
    }
}
