package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import java.util.EnumMap;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Los valores de las 15 métricas de un jugador en un partido. Es inmutable.
 *
 * <p>Guarda siempre las 15 métricas, con 0 en las que no se informaron. Así dos valores con los
 * mismos números son iguales aunque uno traiga el 0 explícito y el otro no.</p>
 */
public final class MetricCounts {
    private static final int MISSING = 0;

    private final Map<Metric, Integer> counts;

    private MetricCounts(Map<Metric, Integer> counts) {
        this.counts = counts;
    }

    /**
     * Copia el mapa: cambiar el original después no lo afecta.
     */
    public static MetricCounts of(Map<Metric, Integer> counts) {
        Map<Metric, Integer> copy = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            copy.put(metric, requireNonNegative(counts.getOrDefault(metric, MISSING)));
        }
        return new MetricCounts(copy);
    }

    public static MetricCounts empty() {
        return of(Map.of());
    }

    public int get(Metric metric) {
        return counts.get(metric);
    }

    /**
     * Una copia con ese valor en la métrica. El original no cambia.
     */
    public MetricCounts with(Metric metric, int value) {
        Map<Metric, Integer> copy = new EnumMap<>(counts);
        copy.put(metric, requireNonNegative(value));
        return new MetricCounts(copy);
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new CatalogInvariantException("Una métrica no puede ser negativa.");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MetricCounts metricCounts && counts.equals(metricCounts.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return "MetricCounts" + counts;
    }
}
