package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * El rendimiento de un jugador en un partido: titular, minutos, las 15 métricas, el rating y la
 * figura (FR-002). Una vez guardado, nunca cambia.
 *
 * <p>El rating queda con dos decimales ({@code 6} pasa a {@code 6.00}), que es como lo guarda la
 * tabla: así un rendimiento guardado y el leído de la base son iguales.</p>
 */
public record PlayerMatchStats(Long id, Player player, Match match, boolean starter, int minutesPlayed,
                               MetricCounts counts, BigDecimal rating, boolean manOfTheMatch) {
    private static final BigDecimal MAX_RATING = BigDecimal.TEN;
    private static final int RATING_SCALE = 2;

    public PlayerMatchStats {
        if (player == null) {
            throw new CatalogInvariantException("El jugador de la estadística es obligatorio.");
        }
        if (match == null) {
            throw new CatalogInvariantException("El partido de la estadística es obligatorio.");
        }
        if (minutesPlayed < 0) {
            throw new CatalogInvariantException("Los minutos jugados no pueden ser negativos.");
        }
        if (rating != null) {
            rating = requireValidRating(rating);
        }
        counts = counts == null ? MetricCounts.empty() : counts;
    }

    /**
     * Entre 0 y 10, con a lo sumo dos decimales (FR-008). {@code 6.00} es válido: los ceros del
     * final no cuentan como decimales.
     */
    private static BigDecimal requireValidRating(BigDecimal rating) {
        boolean inRange = rating.signum() >= 0 && rating.compareTo(MAX_RATING) <= 0;
        if (!inRange || rating.stripTrailingZeros().scale() > RATING_SCALE) {
            throw new CatalogInvariantException("El rating tiene que estar entre 0 y 10, con a lo sumo dos decimales.");
        }
        return rating.setScale(RATING_SCALE);
    }
}
