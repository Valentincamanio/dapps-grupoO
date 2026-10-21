package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.MetricDirection;

/**
 * Compara una métrica de un grupo de pares y la lleva a un valor entre −1 y +1 (research D6,
 * FR-018 a FR-020).
 *
 * <p>Posición relativa = (cantidad de pares peores + la mitad de los que empatan, él incluido) /
 * cantidad de pares, a escala 6 {@code HALF_UP}; la métrica normalizada es {@code 2 × posición −
 * 1}, a partir de la posición ya redondeada. Si todos empatan, o hay un solo par, la posición es
 * 0,5 y la métrica 0: la fórmula lo da sola.</p>
 *
 * <p>Los valores se ordenan una vez ({@code O(n log n)}) y cada valor distinto se resuelve con los
 * acumulados: no se compara a cada jugador contra todos.</p>
 */
public final class MetricNormalization {
    public static final int SCALE = 6;

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private MetricNormalization() {
    }

    /**
     * La métrica normalizada de cada jugador del grupo. Los que no tienen la métrica no tienen que
     * venir en el mapa (no entran en la comparación, FR-021) y tampoco figuran en el resultado.
     */
    public static Map<Long, BigDecimal> normalize(Map<Long, BigDecimal> valuesByPlayer, MetricDirection direction) {
        var normalizedByValue = normalizedByValue(countByValue(valuesByPlayer), valuesByPlayer.size(), direction);
        var normalized = new HashMap<Long, BigDecimal>();
        valuesByPlayer.forEach((playerId, value) -> normalized.put(playerId, normalizedByValue.get(value)));
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * Cuántos pares tienen cada valor, en orden ascendente. El {@link TreeMap} compara con
     * {@code compareTo}: 1 y 1,00 son el mismo valor.
     */
    private static TreeMap<BigDecimal, Integer> countByValue(Map<Long, BigDecimal> valuesByPlayer) {
        var counts = new TreeMap<BigDecimal, Integer>();
        valuesByPlayer.forEach((playerId, value) -> {
            if (playerId == null || value == null) {
                throw new QuoteInvariantException("No se puede normalizar una métrica sin jugador o sin valor.");
            }
            counts.merge(value, 1, Integer::sum);
        });
        return counts;
    }

    private static TreeMap<BigDecimal, BigDecimal> normalizedByValue(TreeMap<BigDecimal, Integer> counts, int peers,
                                                                     MetricDirection direction) {
        var normalized = new TreeMap<BigDecimal, BigDecimal>();
        var lower = 0;
        for (var entry : counts.entrySet()) {
            int ties = entry.getValue();
            int higher = peers - lower - ties;
            int worse = direction == MetricDirection.HIGHER_IS_BETTER ? lower : higher;
            normalized.put(entry.getKey(), normalize(relativePosition(worse, ties, peers)));
            lower += ties;
        }
        return normalized;
    }

    /**
     * {@code (peores + iguales / 2) / pares}, calculado como {@code (2 × peores + iguales) / (2 ×
     * pares)} para dividir una sola vez.
     */
    private static BigDecimal relativePosition(int worse, int ties, int peers) {
        return BigDecimal.valueOf(2L * worse + ties).divide(BigDecimal.valueOf(2L * peers), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal relativePosition) {
        return relativePosition.multiply(TWO).subtract(BigDecimal.ONE);
    }
}
