package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Una fila de la simulación (research D19): un jugador activo, su cotización actual guardada
 * ({@code null} si no tiene) y la que tendría con la estrategia simulada. No se guarda.
 */
public record SimulatedQuote(Player player, BigDecimal currentValue, BigDecimal simulatedValue) {
    public SimulatedQuote {
        if (player == null || simulatedValue == null) {
            throw new QuoteInvariantException("Una cotización simulada necesita el jugador y el valor simulado.");
        }
    }

    /**
     * La simulada menos la actual, con signo. Sin cotización actual, contra los 100,00 iniciales.
     */
    public BigDecimal difference() {
        return simulatedValue.subtract(currentValue == null ? Quote.INITIAL_VALUE : currentValue);
    }
}
