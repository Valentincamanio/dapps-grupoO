package ar.edu.unq.desapp.futbolmarket.persistence.mapper.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategyWeightSQL;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Traduce las filas de {@code strategies} y {@code strategy_weights} al catálogo, campo a campo.
 * Las versiones se agrupan por estrategia con el id de su raíz (la versión 1).
 */
@Component
public class StrategyMapper {
    public static final String DEFAULT_WEIGHT_SET = "DEFAULT";

    public StrategyCatalog toCatalog(List<StrategySQL> rows) {
        Map<Long, List<StrategySQL>> byStrategy = rows.stream()
                .collect(Collectors.groupingBy(StrategySQL::strategyId, TreeMap::new, Collectors.toList()));
        List<Strategy> strategies = byStrategy.entrySet().stream()
                .map(entry -> toStrategy(entry.getKey(), entry.getValue()))
                .toList();
        return new StrategyCatalog(strategies, activeVersionId(rows));
    }

    public StrategyVersion toVersion(StrategySQL row) {
        Map<String, Map<String, BigDecimal>> weightsBySet = row.getWeights().stream()
                .collect(Collectors.groupingBy(StrategyWeightSQL::getWeightSet,
                        Collectors.toMap(StrategyWeightSQL::getMetric, StrategyWeightSQL::getWeight)));
        Map<Position, WeightSet> positionWeights = new EnumMap<>(Position.class);
        weightsBySet.forEach((set, weights) -> {
            if (!DEFAULT_WEIGHT_SET.equals(set)) {
                positionWeights.put(Position.valueOf(set), toWeightSet(weights));
            }
        });
        return new StrategyVersion(row.getId(), row.strategyId(), row.getName(), row.getVersionNumber(),
                row.getScaleFactor(), row.getMinimumValue(),
                toWeightSet(weightsBySet.getOrDefault(DEFAULT_WEIGHT_SET, Map.of())),
                positionWeights, row.getCreatedAt());
    }

    /**
     * La fila de una versión nueva con sus pesos. {@code root} es la fila de la versión 1, o
     * {@code null} si la versión es la 1. Los pesos en 0 ya no figuran en el {@link WeightSet}.
     */
    public StrategySQL toSQL(StrategyVersion version, StrategySQL root, boolean active) {
        StrategySQL row = new StrategySQL(root, version.name(), version.version(), version.scaleFactor(),
                version.minimumValue(), active);
        addWeights(row, DEFAULT_WEIGHT_SET, version.defaultWeights());
        version.positionWeights().forEach((position, weights) -> addWeights(row, position.name(), weights));
        return row;
    }

    private Strategy toStrategy(Long strategyId, List<StrategySQL> rows) {
        List<StrategyVersion> versions = rows.stream()
                .sorted(Comparator.comparingInt(StrategySQL::getVersionNumber))
                .map(this::toVersion)
                .toList();
        return new Strategy(strategyId, versions.getFirst().name(), versions);
    }

    private static WeightSet toWeightSet(Map<String, BigDecimal> weights) {
        Map<ValuationMetric, BigDecimal> byMetric = new EnumMap<>(ValuationMetric.class);
        weights.forEach((metric, weight) -> byMetric.put(ValuationMetric.valueOf(metric), weight));
        return new WeightSet(byMetric);
    }

    private static void addWeights(StrategySQL row, String weightSet, WeightSet weights) {
        weights.weights().forEach((metric, weight) -> row.addWeight(weightSet, metric.name(), weight));
    }

    private static Long activeVersionId(List<StrategySQL> rows) {
        List<Long> active = rows.stream().filter(StrategySQL::isActive).map(StrategySQL::getId).toList();
        if (active.size() > 1) {
            throw new StrategyInvariantException("Hay " + active.size() + " versiones marcadas como activas.");
        }
        return active.isEmpty() ? null : active.getFirst();
    }
}
