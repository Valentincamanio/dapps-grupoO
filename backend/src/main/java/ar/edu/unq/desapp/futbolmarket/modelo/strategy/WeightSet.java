package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidWeightsException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Un juego de pesos por métrica (el por defecto o el de una posición). Cada peso es ≥ 0, con a lo
 * sumo dos decimales, y la suma es exactamente 1,00. Una métrica que no figura pesa 0.
 *
 * <p>El mapa se copia a un {@link EnumMap} inmutable: el orden de iteración es el de
 * {@link ValuationMetric}, así cualquier suma sobre los pesos es determinista (FR-026). Los pesos
 * se guardan con escala 2 y los de valor 0 se descartan (pesan lo mismo que no figurar).
 */
public record WeightSet(Map<ValuationMetric, BigDecimal> weights) {
    public static final String DEFAULT_LABEL = "por defecto";

    private static final int MAX_DECIMALS = 2;
    private static final BigDecimal EXPECTED_SUM = BigDecimal.ONE;

    public WeightSet {
        weights = validated(null, weights);
    }

    /**
     * Construye el juego desde los nombres que llegan en un request. {@code label} identifica el
     * juego en los mensajes ("por defecto", "delanteros").
     */
    public static WeightSet of(String label, Map<String, BigDecimal> weightsByName) {
        if (weightsByName == null) {
            return new WeightSet(validated(label, null));
        }
        var weights = new EnumMap<ValuationMetric, BigDecimal>(ValuationMetric.class);
        weightsByName.forEach((name, weight) -> weights.put(ValuationMetric.fromName(name), weight));
        return new WeightSet(validated(label, weights));
    }

    /**
     * Cómo se nombra el juego de una posición en los mensajes de error.
     */
    public static String labelOf(Position position) {
        return switch (position) {
            case GOALKEEPER -> "de arqueros";
            case DEFENDER -> "de defensores";
            case MIDFIELDER -> "de mediocampistas";
            case FORWARD -> "de delanteros";
        };
    }

    /**
     * El peso de la métrica, o 0 si no figura.
     */
    public BigDecimal weight(ValuationMetric metric) {
        return weights.getOrDefault(metric, BigDecimal.ZERO);
    }

    /**
     * Las métricas con peso mayor que 0: las únicas que hace falta normalizar (D6).
     */
    public Set<ValuationMetric> usedMetrics() {
        if (weights.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(weights.keySet()));
    }

    private static Map<ValuationMetric, BigDecimal> validated(String label, Map<ValuationMetric, BigDecimal> weights) {
        var setName = label == null ? "" : " " + label;
        if (weights == null) {
            throw new InvalidWeightsException("Los pesos" + setName + " son obligatorios.");
        }
        var copy = new EnumMap<ValuationMetric, BigDecimal>(ValuationMetric.class);
        var sum = BigDecimal.ZERO;
        for (var entry : weights.entrySet()) {
            var weight = requireValid(entry.getKey(), entry.getValue());
            sum = sum.add(weight);
            if (weight.signum() > 0) {
                copy.put(entry.getKey(), weight.setScale(MAX_DECIMALS));
            }
        }
        if (sum.compareTo(EXPECTED_SUM) != 0) {
            throw new InvalidWeightsException("Los pesos" + setName + " deben sumar 1,00 y suman "
                    + format(sum) + ".");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static BigDecimal requireValid(ValuationMetric metric, BigDecimal weight) {
        if (weight == null) {
            throw new InvalidWeightsException("El peso de " + metric + " es obligatorio.");
        }
        if (weight.signum() < 0) {
            throw new InvalidWeightsException("El peso de " + metric + " no puede ser negativo.");
        }
        if (weight.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new InvalidWeightsException("El peso de " + metric + " admite a lo sumo dos decimales.");
        }
        return weight;
    }

    private static String format(BigDecimal sum) {
        return sum.setScale(MAX_DECIMALS).toPlainString().replace('.', ',');
    }
}
