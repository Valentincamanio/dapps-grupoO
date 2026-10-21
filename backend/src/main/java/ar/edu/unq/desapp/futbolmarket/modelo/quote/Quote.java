package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * El valor del 100 % de un jugador desde un instante (research D10). Es inmutable y no tiene
 * operaciones de cambio: una cotización guardada no se modifica ni se borra (FR-005).
 *
 * <p>Referencia al jugador por id y a la versión de la estrategia por una referencia liviana, para
 * leer el historial sin cargar jugadores, equipos ni pesos. {@code baseValue} es el valor desde el
 * que se calculó (nulo en la {@code INITIAL}); con él la variación sale sin buscar la cotización
 * anterior.</p>
 */
public record Quote(Long id, long playerId, QuoteWeek week, Instant validFrom, BigDecimal value,
                    BigDecimal baseValue, BigDecimal score, QuoteType type, StrategyVersionRef strategy) {
    public static final BigDecimal INITIAL_VALUE = new BigDecimal("100.00");
    public static final int VALUE_SCALE = 2;
    public static final int SCORE_SCALE = 6;
    public static final int TOKEN_PRICE_SCALE = 4;
    public static final int PERCENT_SCALE = 2;

    private static final BigDecimal TOKENS_PER_PLAYER = new BigDecimal("100");
    private static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100");

    public Quote {
        requireNotNull(week, "La semana de la cotización es obligatoria.");
        requireNotNull(validFrom, "La vigencia de la cotización es obligatoria.");
        requireNotNull(type, "El tipo de la cotización es obligatorio.");
        requireNotNull(strategy, "La estrategia de la cotización es obligatoria.");
        requireValidValue(value, "El valor de la cotización");
        requireValidScore(score);
        requireBaseValueMatchesType(type, baseValue);
        requireValidFromMatchesType(type, week, validFrom);
    }

    /**
     * El precio de un token: el valor del 100 % dividido en 100 tokens. Con dos decimales de valor
     * entra exacto en cuatro.
     */
    public BigDecimal tokenPrice() {
        return value.divide(TOKENS_PER_PLAYER, TOKEN_PRICE_SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * La variación respecto del valor base: vacía en la {@code INITIAL}, que no tiene base.
     */
    public Optional<QuoteVariation> variation() {
        if (baseValue == null) {
            return Optional.empty();
        }
        BigDecimal amount = value.subtract(baseValue);
        BigDecimal percent = amount.multiply(HUNDRED_PERCENT).divide(baseValue, PERCENT_SCALE, RoundingMode.HALF_UP);
        return Optional.of(new QuoteVariation(amount, percent));
    }

    /**
     * Si la cotización ya regía a ese instante.
     */
    public boolean isCurrentAt(Instant instant) {
        return !validFrom.isAfter(instant);
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new QuoteInvariantException(message);
        }
    }

    private static void requireValidValue(BigDecimal value, String what) {
        if (value == null || value.scale() != VALUE_SCALE || value.signum() <= 0) {
            throw new QuoteInvariantException(what + " tiene que ser mayor que 0 y tener dos decimales: " + value + ".");
        }
    }

    private static void requireValidScore(BigDecimal score) {
        boolean inRange = score != null && score.abs().compareTo(BigDecimal.ONE) <= 0;
        if (!inRange || score.scale() != SCORE_SCALE) {
            throw new QuoteInvariantException("El score tiene que estar entre -1 y +1 con seis decimales: " + score + ".");
        }
    }

    private static void requireBaseValueMatchesType(QuoteType type, BigDecimal baseValue) {
        if (type == QuoteType.INITIAL) {
            if (baseValue != null) {
                throw new QuoteInvariantException("Una cotización inicial no tiene valor base.");
            }
            return;
        }
        requireValidValue(baseValue, "El valor base de una cotización " + type);
    }

    private static void requireValidFromMatchesType(QuoteType type, QuoteWeek week, Instant validFrom) {
        if (type == QuoteType.INITIAL && !validFrom.equals(week.start())) {
            throw new QuoteInvariantException("Una cotización inicial rige desde el inicio de su semana.");
        }
        if (type == QuoteType.CALCULATED && !validFrom.equals(week.end())) {
            throw new QuoteInvariantException("Una cotización calculada rige desde el cierre de su semana.");
        }
    }
}
