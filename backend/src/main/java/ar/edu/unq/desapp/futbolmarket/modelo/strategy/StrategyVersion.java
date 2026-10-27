package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidMinimumValueException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidScaleFactorException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidWeightsException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Una versión de una estrategia: sus parámetros no cambian nunca (D13). Cada edición crea otra.
 *
 * <p>{@code id} y {@code strategyId} (el id de la versión 1) son nulos hasta que la versión se
 * guarda; {@code createdAt} lo asigna la persistencia al insertarla. {@code name} es el de la
 * estrategia, para armar {@link #ref()} sin volver al catálogo.
 */
public record StrategyVersion(Long id, Long strategyId, String name, int version, BigDecimal scaleFactor,
                              BigDecimal minimumValue, WeightSet defaultWeights,
                              Map<Position, WeightSet> positionWeights, Instant createdAt) {
    public static final BigDecimal DEFAULT_SCALE_FACTOR = BigDecimal.TEN;
    public static final BigDecimal DEFAULT_MINIMUM_VALUE = new BigDecimal("10.00");
    public static final BigDecimal MIN_ALLOWED_MINIMUM = new BigDecimal("0.01");

    private static final int VALUE_SCALE = 2;
    private static final int SCORE_SCALE = 6;

    public StrategyVersion {
        if (version < 1) {
            throw new StrategyInvariantException("El número de versión debe ser al menos 1.");
        }
        scaleFactor = requireValidScaleFactor(scaleFactor);
        minimumValue = requireValidMinimum(minimumValue);
        if (defaultWeights == null) {
            throw new InvalidWeightsException("Los pesos por defecto son obligatorios.");
        }
        positionWeights = copyOf(positionWeights);
    }

    /**
     * Los pesos de la posición si los tiene; si no, los por defecto (FR-023).
     */
    public WeightSet weightsFor(Position position) {
        return positionWeights.getOrDefault(position, defaultWeights);
    }

    /**
     * Las métricas que hay que normalizar para los jugadores de esa posición.
     */
    public Set<ValuationMetric> usedMetrics(Position position) {
        return weightsFor(position).usedMetrics();
    }

    /**
     * {@code Σ peso × métrica normalizada}, sin redondeos intermedios y redondeado una sola vez a
     * escala 6 (D4). Una métrica sin valor (no calculable) suma 0.
     */
    public BigDecimal scoreFor(Position position, Map<ValuationMetric, BigDecimal> normalizedMetrics) {
        var sum = BigDecimal.ZERO;
        for (var entry : weightsFor(position).weights().entrySet()) {
            var value = normalizedMetrics.get(entry.getKey());
            if (value != null) {
                sum = sum.add(entry.getValue().multiply(value));
            }
        }
        return sum.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * {@code max(mínimo, base + score × factor)}, redondeado una sola vez a 2 decimales (FR-024).
     */
    public BigDecimal valueFor(BigDecimal baseValue, BigDecimal score) {
        var raw = baseValue.add(score.multiply(scaleFactor));
        return raw.max(minimumValue).setScale(VALUE_SCALE, RoundingMode.HALF_UP);
    }

    public StrategyVersionRef ref() {
        return new StrategyVersionRef(id, strategyId, name, version);
    }

    private static BigDecimal requireValidScaleFactor(BigDecimal scaleFactor) {
        if (scaleFactor == null || scaleFactor.signum() <= 0) {
            throw new InvalidScaleFactorException("El factor de escala debe ser mayor que 0.");
        }
        if (hasTooManyDecimals(scaleFactor)) {
            throw new InvalidScaleFactorException("El factor de escala admite a lo sumo dos decimales.");
        }
        return scaleFactor.setScale(VALUE_SCALE);
    }

    private static BigDecimal requireValidMinimum(BigDecimal minimumValue) {
        if (minimumValue == null || minimumValue.compareTo(MIN_ALLOWED_MINIMUM) < 0) {
            throw new InvalidMinimumValueException("La cotización mínima debe ser al menos 0,01.");
        }
        if (hasTooManyDecimals(minimumValue)) {
            throw new InvalidMinimumValueException("La cotización mínima admite a lo sumo dos decimales.");
        }
        return minimumValue.setScale(VALUE_SCALE);
    }

    private static boolean hasTooManyDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > VALUE_SCALE;
    }

    private static Map<Position, WeightSet> copyOf(Map<Position, WeightSet> positionWeights) {
        var copy = new EnumMap<Position, WeightSet>(Position.class);
        if (positionWeights != null) {
            positionWeights.forEach((position, weights) -> {
                if (weights == null) {
                    throw new InvalidWeightsException("Los pesos " + WeightSet.labelOf(position) + " son obligatorios.");
                }
                copy.put(position, weights);
            });
        }
        return Collections.unmodifiableMap(copy);
    }
}
