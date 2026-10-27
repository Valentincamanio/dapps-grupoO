package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidWeightsException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.UnknownMetricException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightSetTest {

    @Test
    void aceptaPesosQueSumanExactamenteUno() {
        var weights = WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of(
                "GOALS", new BigDecimal("0.1"),
                "ASSISTS", new BigDecimal("0.2"),
                "RATING", new BigDecimal("0.7")));

        assertThat(weights.weight(ValuationMetric.GOALS)).isEqualByComparingTo("0.10");
        assertThat(weights.weight(ValuationMetric.ASSISTS)).isEqualByComparingTo("0.20");
        assertThat(weights.weight(ValuationMetric.RATING)).isEqualByComparingTo("0.70");
    }

    @Test
    void rechazaUnaSumaDistintaDeUnoInformandoLaSumaYElJuego() {
        var weights = Map.of(
                "GOALS", new BigDecimal("0.50"),
                "ASSISTS", new BigDecimal("0.45"));

        assertThatThrownBy(() -> WeightSet.of(WeightSet.labelOf(Position.FORWARD), weights))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessage("Los pesos de delanteros deben sumar 1,00 y suman 0,95.");
    }

    @Test
    void rechazaUnaSumaMayorQueUno() {
        var weights = Map.of(
                "GOALS", new BigDecimal("0.60"),
                "ASSISTS", new BigDecimal("0.45"));

        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL, weights))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessage("Los pesos por defecto deben sumar 1,00 y suman 1,05.");
    }

    @Test
    void rechazaUnPesoNegativo() {
        var weights = Map.of(
                "GOALS", new BigDecimal("1.10"),
                "FOULS_COMMITTED", new BigDecimal("-0.10"));

        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL, weights))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessage("El peso de FOULS_COMMITTED no puede ser negativo.");
    }

    @Test
    void rechazaUnPesoConTresDecimales() {
        var weights = Map.of(
                "GOALS", new BigDecimal("0.505"),
                "ASSISTS", new BigDecimal("0.495"));

        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL, weights))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessageContaining("admite a lo sumo dos decimales");
    }

    @Test
    void aceptaCerosALaDerechaMasAllaDeLosDosDecimales() {
        var weights = WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of("GOALS", new BigDecimal("1.000")));

        assertThat(weights.weight(ValuationMetric.GOALS)).isEqualByComparingTo("1");
    }

    @Test
    void rechazaUnaMetricaDesconocida() {
        var weights = Map.of("GOLES", BigDecimal.ONE);

        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL, weights))
                .isInstanceOf(UnknownMetricException.class)
                .hasMessage("La métrica 'GOLES' no existe.");
    }

    @Test
    void rechazaUnJuegoFaltante() {
        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL, null))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessage("Los pesos por defecto son obligatorios.");
    }

    @Test
    void unaMetricaAusentePesaCero() {
        var weights = WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of("GOALS", BigDecimal.ONE));

        assertThat(weights.weight(ValuationMetric.TACKLES)).isEqualByComparingTo("0");
    }

    @Test
    void usedMetricsExcluyeLasDePesoCero() {
        var input = new LinkedHashMap<String, BigDecimal>();
        input.put("GOALS", new BigDecimal("0.60"));
        input.put("TACKLES", new BigDecimal("0.00"));
        input.put("RATING", new BigDecimal("0.40"));

        var weights = WeightSet.of(WeightSet.DEFAULT_LABEL, input);

        assertThat(weights.usedMetrics()).containsExactly(ValuationMetric.GOALS, ValuationMetric.RATING);
    }

    @Test
    void esInmutableYNoDependeDelMapaOriginal() {
        var input = new LinkedHashMap<ValuationMetric, BigDecimal>();
        input.put(ValuationMetric.GOALS, BigDecimal.ONE);
        var weights = new WeightSet(input);

        input.put(ValuationMetric.ASSISTS, BigDecimal.ONE);

        assertThat(weights.weights()).containsOnlyKeys(ValuationMetric.GOALS);
        assertThatThrownBy(() -> weights.weights().put(ValuationMetric.TACKLES, BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dosJuegosConLosMismosPesosSonIguales() {
        var a = WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of("GOALS", new BigDecimal("1")));
        var b = WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of("GOALS", new BigDecimal("1.00")));

        assertThat(a).isEqualTo(b);
    }
}
