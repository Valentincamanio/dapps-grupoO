package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.AERIALS_WON;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.ASSISTS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.CLEAN_SHEETS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.CLEARANCES;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.FOULS_COMMITTED;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.GOALS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.INTERCEPTIONS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.KEY_PASSES;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.MINUTES_PLAYED;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.PASSES;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.PASS_ACCURACY;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.RATING;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.RED_CARDS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.SHOTS;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.SHOTS_ON_TARGET;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.SUCCESSFUL_DRIBBLES;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.TACKLES;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.TEAM_RESULT;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric.YELLOW_CARDS;
import static java.util.Map.entry;

/**
 * Las dos estrategias de arranque (D13) como datos puros. La semilla las crea en este orden: la
 * primera que entra en un catálogo vacío queda activa, así que la A es la activa de arranque.
 */
public final class StartingStrategies {
    public static final String MATCH_PERFORMANCE_NAME = "Rendimiento de partido";
    public static final String BALANCED_BY_POSITION_NAME = "Equilibrada por posición";

    /**
     * Una estrategia de arranque: lo que se le pasa a {@link StrategyCatalog#create}.
     */
    public record Definition(String name, BigDecimal scaleFactor, BigDecimal minimumValue,
                             WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
    }

    /**
     * A: los mismos pesos para todas las posiciones (solo el juego por defecto).
     */
    public static final Definition MATCH_PERFORMANCE = new Definition(
            MATCH_PERFORMANCE_NAME,
            StrategyVersion.DEFAULT_SCALE_FACTOR,
            StrategyVersion.DEFAULT_MINIMUM_VALUE,
            weights(
                    entry(GOALS, w("0.25")),
                    entry(ASSISTS, w("0.15")),
                    entry(SHOTS, w("0.10")),
                    entry(KEY_PASSES, w("0.10")),
                    entry(SUCCESSFUL_DRIBBLES, w("0.10")),
                    entry(TACKLES, w("0.10")),
                    entry(RATING, w("0.20"))),
            Map.of());

    private static final WeightSet BALANCED_MIDFIELDER = weights(
            entry(GOALS, w("0.10")),
            entry(ASSISTS, w("0.14")),
            entry(KEY_PASSES, w("0.16")),
            entry(SUCCESSFUL_DRIBBLES, w("0.06")),
            entry(PASS_ACCURACY, w("0.10")),
            entry(PASSES, w("0.06")),
            entry(TACKLES, w("0.08")),
            entry(INTERCEPTIONS, w("0.06")),
            entry(RATING, w("0.18")),
            entry(TEAM_RESULT, w("0.02")),
            entry(MINUTES_PLAYED, w("0.02")),
            entry(YELLOW_CARDS, w("0.02")));

    /**
     * B: un juego por posición. El por defecto no se usa mientras existan solo esas cuatro; se deja
     * igual al de mediocampista (D13).
     */
    public static final Definition BALANCED_BY_POSITION = new Definition(
            BALANCED_BY_POSITION_NAME,
            StrategyVersion.DEFAULT_SCALE_FACTOR,
            StrategyVersion.DEFAULT_MINIMUM_VALUE,
            BALANCED_MIDFIELDER,
            Map.of(
                    Position.FORWARD, weights(
                            entry(GOALS, w("0.28")),
                            entry(ASSISTS, w("0.14")),
                            entry(SHOTS_ON_TARGET, w("0.12")),
                            entry(KEY_PASSES, w("0.06")),
                            entry(SUCCESSFUL_DRIBBLES, w("0.08")),
                            entry(RATING, w("0.20")),
                            entry(TEAM_RESULT, w("0.05")),
                            entry(MINUTES_PLAYED, w("0.03")),
                            entry(FOULS_COMMITTED, w("0.01")),
                            entry(YELLOW_CARDS, w("0.02")),
                            entry(RED_CARDS, w("0.01"))),
                    Position.MIDFIELDER, BALANCED_MIDFIELDER,
                    Position.DEFENDER, weights(
                            entry(PASS_ACCURACY, w("0.06")),
                            entry(PASSES, w("0.04")),
                            entry(TACKLES, w("0.16")),
                            entry(INTERCEPTIONS, w("0.16")),
                            entry(CLEARANCES, w("0.10")),
                            entry(AERIALS_WON, w("0.08")),
                            entry(RATING, w("0.22")),
                            entry(TEAM_RESULT, w("0.02")),
                            entry(CLEAN_SHEETS, w("0.08")),
                            entry(MINUTES_PLAYED, w("0.02")),
                            entry(FOULS_COMMITTED, w("0.02")),
                            entry(YELLOW_CARDS, w("0.02")),
                            entry(RED_CARDS, w("0.02"))),
                    Position.GOALKEEPER, weights(
                            entry(PASS_ACCURACY, w("0.10")),
                            entry(PASSES, w("0.04")),
                            entry(CLEARANCES, w("0.08")),
                            entry(AERIALS_WON, w("0.07")),
                            entry(RATING, w("0.40")),
                            entry(TEAM_RESULT, w("0.02")),
                            entry(CLEAN_SHEETS, w("0.25")),
                            entry(MINUTES_PLAYED, w("0.02")),
                            entry(YELLOW_CARDS, w("0.01")),
                            entry(RED_CARDS, w("0.01")))));

    private StartingStrategies() {
    }

    /**
     * Las dos, en el orden en que se siembran.
     */
    public static List<Definition> all() {
        return List.of(MATCH_PERFORMANCE, BALANCED_BY_POSITION);
    }

    @SafeVarargs
    private static WeightSet weights(Map.Entry<ValuationMetric, BigDecimal>... entries) {
        return new WeightSet(Map.ofEntries(entries));
    }

    private static BigDecimal w(String weight) {
        return new BigDecimal(weight);
    }
}
