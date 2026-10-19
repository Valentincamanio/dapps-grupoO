package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.UnknownMetricException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuationMetricTest {

    @Test
    void hayDiecinueveMetricas() {
        assertThat(ValuationMetric.values()).hasSize(19);
    }

    @Test
    void soloFaltasYTarjetasSonDeMenosEsMejor() {
        var inverted = Arrays.stream(ValuationMetric.values())
                .filter(metric -> metric.direction() == MetricDirection.LOWER_IS_BETTER)
                .toList();

        assertThat(inverted).containsExactlyInAnyOrder(
                ValuationMetric.FOULS_COMMITTED, ValuationMetric.YELLOW_CARDS, ValuationMetric.RED_CARDS);
    }

    @Test
    void lasDemasSonDeMasEsMejor() {
        assertThat(ValuationMetric.GOALS.direction()).isEqualTo(MetricDirection.HIGHER_IS_BETTER);
        assertThat(ValuationMetric.CLEAN_SHEETS.direction()).isEqualTo(MetricDirection.HIGHER_IS_BETTER);
        assertThat(ValuationMetric.RATING.direction()).isEqualTo(MetricDirection.HIGHER_IS_BETTER);
    }

    @Test
    void fromNameResuelveUnNombreValido() {
        var metric = ValuationMetric.fromName("TACKLES");

        assertThat(metric).isEqualTo(ValuationMetric.TACKLES);
    }

    @Test
    void fromNameRechazaUnaMetricaDesconocidaIndicandoElValor() {
        assertThatThrownBy(() -> ValuationMetric.fromName("GOLES"))
                .isInstanceOf(UnknownMetricException.class)
                .hasMessage("La métrica 'GOLES' no existe.");
    }

    @Test
    void fromNameDistingueMayusculas() {
        assertThatThrownBy(() -> ValuationMetric.fromName("goals"))
                .isInstanceOf(UnknownMetricException.class)
                .hasMessage("La métrica 'goals' no existe.");
    }
}
