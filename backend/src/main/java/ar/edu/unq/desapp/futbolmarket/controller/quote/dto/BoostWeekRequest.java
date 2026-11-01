package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /quotes/boosts/{week}}: los potenciados de la semana, en orden. Que sean
 * cuatro distintos o ninguno, y el rango del porcentaje, los valida el modelo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoostWeekRequest(
        @NotNull(message = "La lista de potenciados es obligatoria; vacía, la semana queda sin potenciados.")
        List<@Valid @NotNull(message = "Cada potenciado es obligatorio.") Boost> players) {

    public record Boost(
            @NotNull(message = "El identificador del jugador es obligatorio.")
            @Min(value = 1, message = "El identificador del jugador debe ser mayor que 0.")
            Long playerId,
            @NotNull(message = "El porcentaje es obligatorio.")
            Integer percent) {
    }

    public List<PlayerBoost> boosts() {
        return players.stream().map(boost -> new PlayerBoost(boost.playerId(), boost.percent())).toList();
    }
}
