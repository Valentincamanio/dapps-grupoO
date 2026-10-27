package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.StrategyParametersRequest;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteSimulationService;
import jakarta.validation.constraints.Min;

/**
 * Cuerpo de {@code POST /quotes/simulate}: una estrategia guardada o parámetros. Que venga uno solo
 * de los dos lo valida {@link QuoteSimulationService}; los parámetros, el modelo, con las mismas
 * reglas que al crear una estrategia (FR-057).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationRequest(
        @Min(value = 1, message = "El identificador de la estrategia debe ser mayor que 0.")
        Long strategyId,
        StrategyParametersRequest parameters) {

    /**
     * Los parámetros ya validados por el modelo, o {@code null} si no vinieron.
     */
    public QuoteSimulationService.Parameters simulationParameters() {
        if (parameters == null) {
            return null;
        }
        return new QuoteSimulationService.Parameters(parameters.scaleFactor(), parameters.minimumValue(),
                parameters.defaultWeightSet(), parameters.positionWeightSets());
    }
}
