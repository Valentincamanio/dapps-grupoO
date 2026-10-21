package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /strategies}: el nombre y los parámetros, en el mismo nivel.
 *
 * <p>Ignora las propiedades desconocidas a propósito: crear una estrategia no admite la opción de
 * recotización, y si el cliente la manda se descarta (research D14). El resto de las reglas (largo
 * del nombre, nombre repetido, pesos, factor y mínimo) son del modelo.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateStrategyRequest(
        @NotBlank(message = "El nombre de la estrategia es obligatorio.")
        String name,
        BigDecimal scaleFactor,
        BigDecimal minimumValue,
        Map<String, BigDecimal> defaultWeights,
        Map<Position, Map<String, BigDecimal>> positionWeights) {

    public StrategyParametersRequest parameters() {
        return new StrategyParametersRequest(scaleFactor, minimumValue, defaultWeights, positionWeights);
    }
}
