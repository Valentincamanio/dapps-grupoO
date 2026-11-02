package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidBoostException;

/**
 * Un jugador potenciado en una semana y su porcentaje: en el cierre de esa semana su factor de
 * escala se multiplica por {@code percent / 100}, así su cotización se mueve más que la del resto.
 * Va de 200 % a 400 %, de a 25 %.
 */
public record PlayerBoost(long playerId, int percent) {
    public static final int MIN_PERCENT = 200;
    public static final int MAX_PERCENT = 400;
    public static final int PERCENT_STEP = 25;

    private static final int PERCENT_SCALE = 2;

    public PlayerBoost {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT || percent % PERCENT_STEP != 0) {
            throw new InvalidBoostException("El potenciado va de " + MIN_PERCENT + " % a " + MAX_PERCENT
                    + " %, de a " + PERCENT_STEP + " %, y se pidió " + percent + " %.");
        }
    }

    /**
     * Por cuánto se multiplica el factor de escala: 300 % es 3, 250 % es 2,5.
     */
    public BigDecimal multiplier() {
        return BigDecimal.valueOf(percent).movePointLeft(PERCENT_SCALE);
    }
}
