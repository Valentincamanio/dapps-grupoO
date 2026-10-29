package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de una compra o una venta (research D23). La forma se valida acá; si el jugador existe y se
 * puede operar lo decide el servicio.
 *
 * <p>{@code quantity} es {@link BigDecimal} y no {@code Integer} porque Jackson convierte
 * {@code 1.5} en {@code 1} sin avisar, y una cantidad decimal se rechaza (US1.9). Cualquier otro
 * campo, como un {@code userId}, se ignora: el usuario sale siempre de la credencial (FR-013).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRequest(

        @NotNull(message = PLAYER_REQUIRED_MESSAGE)
        @Min(value = 1, message = PLAYER_ID_MESSAGE)
        Long playerId,

        @NotNull(message = QUANTITY_REQUIRED_MESSAGE)
        @Digits(integer = 3, fraction = 0, message = QUANTITY_MESSAGE)
        @DecimalMin(value = "1", message = QUANTITY_MESSAGE)
        @DecimalMax(value = "100", message = QUANTITY_MESSAGE)
        BigDecimal quantity) {

    public static final String PLAYER_REQUIRED_MESSAGE = "El jugador es obligatorio.";
    public static final String PLAYER_ID_MESSAGE = "El id del jugador tiene que ser mayor o igual a 1.";
    public static final String QUANTITY_REQUIRED_MESSAGE = "La cantidad es obligatoria.";
    public static final String QUANTITY_MESSAGE = "La cantidad tiene que ser un entero entre 1 y 100.";
}
