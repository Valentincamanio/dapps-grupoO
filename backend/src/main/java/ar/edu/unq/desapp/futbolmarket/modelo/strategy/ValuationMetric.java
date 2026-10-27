package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.UnknownMetricException;

import java.util.Arrays;

/**
 * Las 19 métricas semanales que una estrategia puede pesar (D5), con su sentido. Los nombres
 * coinciden con los que recibe la API.
 */
public enum ValuationMetric {
    GOALS(MetricDirection.HIGHER_IS_BETTER),
    ASSISTS(MetricDirection.HIGHER_IS_BETTER),
    SHOTS(MetricDirection.HIGHER_IS_BETTER),
    SHOTS_ON_TARGET(MetricDirection.HIGHER_IS_BETTER),
    KEY_PASSES(MetricDirection.HIGHER_IS_BETTER),
    SUCCESSFUL_DRIBBLES(MetricDirection.HIGHER_IS_BETTER),
    TACKLES(MetricDirection.HIGHER_IS_BETTER),
    INTERCEPTIONS(MetricDirection.HIGHER_IS_BETTER),
    CLEARANCES(MetricDirection.HIGHER_IS_BETTER),
    AERIALS_WON(MetricDirection.HIGHER_IS_BETTER),
    PASSES(MetricDirection.HIGHER_IS_BETTER),
    MINUTES_PLAYED(MetricDirection.HIGHER_IS_BETTER),
    RATING(MetricDirection.HIGHER_IS_BETTER),
    PASS_ACCURACY(MetricDirection.HIGHER_IS_BETTER),
    TEAM_RESULT(MetricDirection.HIGHER_IS_BETTER),
    CLEAN_SHEETS(MetricDirection.HIGHER_IS_BETTER),
    FOULS_COMMITTED(MetricDirection.LOWER_IS_BETTER),
    YELLOW_CARDS(MetricDirection.LOWER_IS_BETTER),
    RED_CARDS(MetricDirection.LOWER_IS_BETTER);

    private final MetricDirection direction;

    ValuationMetric(MetricDirection direction) {
        this.direction = direction;
    }

    public MetricDirection direction() {
        return direction;
    }

    /**
     * Resuelve el nombre exacto que llega en un request. No usa {@code valueOf} para poder
     * informar el valor no admitido con un mensaje propio.
     */
    public static ValuationMetric fromName(String name) {
        return Arrays.stream(values())
                .filter(metric -> metric.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new UnknownMetricException(name));
    }
}
