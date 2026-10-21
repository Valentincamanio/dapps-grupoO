package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidMinimumValueException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidScaleFactorException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidWeightsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyVersionTest {
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));
    private static final WeightSet ALL_TACKLES = new WeightSet(Map.of(ValuationMetric.TACKLES, BigDecimal.ONE));

    @Test
    void elScoreDeLaEstrategiaAEsLaSumaPonderadaDeLasMetricas() {
        var strategyA = strategyA();
        var normalized = Map.of(
                ValuationMetric.GOALS, new BigDecimal("0.8"),
                ValuationMetric.ASSISTS, new BigDecimal("0.4"),
                ValuationMetric.SHOTS, BigDecimal.ZERO,
                ValuationMetric.KEY_PASSES, BigDecimal.ZERO,
                ValuationMetric.SUCCESSFUL_DRIBBLES, new BigDecimal("-0.4"),
                ValuationMetric.TACKLES, new BigDecimal("-0.4"),
                ValuationMetric.RATING, new BigDecimal("0.6"));

        var score = strategyA.scoreFor(Position.FORWARD, normalized);

        assertThat(score).isEqualTo(new BigDecimal("0.300000"));
    }

    @Test
    void unaMetricaSinValorSumaCeroAlScore() {
        var strategyA = strategyA();

        var score = strategyA.scoreFor(Position.DEFENDER, Map.of(ValuationMetric.GOALS, new BigDecimal("0.8")));

        assertThat(score).isEqualTo(new BigDecimal("0.200000"));
    }

    @Test
    void elScoreSeRedondeaUnaSolaVezAEscalaSeis() {
        var weights = new WeightSet(Map.of(
                ValuationMetric.GOALS, new BigDecimal("0.50"),
                ValuationMetric.ASSISTS, new BigDecimal("0.50")));
        var version = version(BigDecimal.TEN, new BigDecimal("10.00"), weights, Map.of());
        var normalized = Map.of(
                ValuationMetric.GOALS, new BigDecimal("0.0000005"),
                ValuationMetric.ASSISTS, new BigDecimal("0.0000005"));

        var score = version.scoreFor(Position.FORWARD, normalized);

        assertThat(score).isEqualTo(new BigDecimal("0.000001"));
    }

    @Test
    void cienConScoreDeTreintaCentesimosPasaACientoTres() {
        var value = strategyA().valueFor(new BigDecimal("100.00"), new BigDecimal("0.300000"));

        assertThat(value).isEqualTo(new BigDecimal("103.00"));
    }

    @Test
    void cientoTresConScoreNegativoDeVeinteCentesimosPasaACientoUno() {
        var value = strategyA().valueFor(new BigDecimal("103.00"), new BigDecimal("-0.200000"));

        assertThat(value).isEqualTo(new BigDecimal("101.00"));
    }

    @Test
    void elValorNoBajaDeLaCotizacionMinima() {
        var value = strategyA().valueFor(new BigDecimal("12.00"), new BigDecimal("-0.500000"));

        assertThat(value).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void cientoTresConScoreDeDiezCentesimosPasaACientoCuatro() {
        var value = strategyA().valueFor(new BigDecimal("103.00"), new BigDecimal("0.100000"));

        assertThat(value).isEqualTo(new BigDecimal("104.00"));
    }

    @Test
    void unScoreCeroConservaElValor() {
        var value = strategyA().valueFor(new BigDecimal("87.45"), new BigDecimal("0.000000"));

        assertThat(value).isEqualTo(new BigDecimal("87.45"));
    }

    @Test
    void elValorRedondeaHaciaArribaDesdeLaMitad() {
        var value = strategyA().valueFor(new BigDecimal("100.00"), new BigDecimal("0.000500"));

        assertThat(value).isEqualTo(new BigDecimal("100.01"));
    }

    @Test
    void elValorRedondeaHaciaAbajoPorDebajoDeLaMitad() {
        var value = strategyA().valueFor(new BigDecimal("100.00"), new BigDecimal("0.000499"));

        assertThat(value).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void unaBasePorDebajoDeUnMinimoQueSubioQuedaEnElMinimo() {
        var version = version(BigDecimal.TEN, new BigDecimal("20.00"), ALL_GOALS, Map.of());

        var value = version.valueFor(new BigDecimal("12.00"), new BigDecimal("0.100000"));

        assertThat(value).isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void weightsForUsaLosDeLaPosicionSiLosTiene() {
        var version = version(BigDecimal.TEN, new BigDecimal("10.00"), ALL_GOALS,
                Map.of(Position.DEFENDER, ALL_TACKLES));

        assertThat(version.weightsFor(Position.DEFENDER)).isEqualTo(ALL_TACKLES);
        assertThat(version.usedMetrics(Position.DEFENDER)).containsExactly(ValuationMetric.TACKLES);
    }

    @Test
    void weightsForCaeEnLosPorDefectoSiLaPosicionNoTieneJuegoPropio() {
        var version = version(BigDecimal.TEN, new BigDecimal("10.00"), ALL_GOALS,
                Map.of(Position.DEFENDER, ALL_TACKLES));

        assertThat(version.weightsFor(Position.FORWARD)).isEqualTo(ALL_GOALS);
        assertThat(version.usedMetrics(Position.GOALKEEPER)).containsExactly(ValuationMetric.GOALS);
    }

    @Test
    void rechazaUnFactorDeEscalaCeroONegativo() {
        assertThatThrownBy(() -> version(BigDecimal.ZERO, new BigDecimal("10.00"), ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidScaleFactorException.class)
                .hasMessage("El factor de escala debe ser mayor que 0.");
        assertThatThrownBy(() -> version(new BigDecimal("-1"), new BigDecimal("10.00"), ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidScaleFactorException.class);
    }

    @Test
    void rechazaUnMinimoMenorQueUnCentesimo() {
        assertThatThrownBy(() -> version(BigDecimal.TEN, new BigDecimal("0.00"), ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidMinimumValueException.class)
                .hasMessage("La cotización mínima debe ser al menos 0,01.");
    }

    @Test
    void aceptaUnMinimoDeUnCentesimo() {
        var version = version(BigDecimal.TEN, new BigDecimal("0.01"), ALL_GOALS, Map.of());

        assertThat(version.minimumValue()).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void rechazaFactorOMinimoConMasDeDosDecimales() {
        assertThatThrownBy(() -> version(new BigDecimal("10.005"), new BigDecimal("10.00"), ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidScaleFactorException.class);
        assertThatThrownBy(() -> version(BigDecimal.TEN, new BigDecimal("10.005"), ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidMinimumValueException.class);
    }

    @Test
    void rechazaUnaVersionSinPesosPorDefecto() {
        assertThatThrownBy(() -> version(BigDecimal.TEN, new BigDecimal("10.00"), null, Map.of()))
                .isInstanceOf(InvalidWeightsException.class)
                .hasMessage("Los pesos por defecto son obligatorios.");
    }

    @Test
    void normalizaFactorYMinimoAEscalaDos() {
        var version = version(BigDecimal.TEN, BigDecimal.TEN, ALL_GOALS, null);

        assertThat(version.scaleFactor()).isEqualTo(new BigDecimal("10.00"));
        assertThat(version.minimumValue()).isEqualTo(new BigDecimal("10.00"));
        assertThat(version.positionWeights()).isEmpty();
    }

    @Test
    void refArmaLaReferenciaLivianaDeLaVersion() {
        var version = new StrategyVersion(12L, 3L, "Ofensiva", 2, BigDecimal.TEN, BigDecimal.TEN,
                ALL_GOALS, Map.of(), null);

        assertThat(version.ref()).isEqualTo(new StrategyVersionRef(12L, 3L, "Ofensiva", 2));
    }

    private static StrategyVersion strategyA() {
        var a = StartingStrategies.MATCH_PERFORMANCE;
        return new StrategyVersion(1L, 1L, a.name(), 1, a.scaleFactor(), a.minimumValue(),
                a.defaultWeights(), a.positionWeights(), null);
    }

    private static StrategyVersion version(BigDecimal factor, BigDecimal minimum, WeightSet defaults,
                                           Map<Position, WeightSet> byPosition) {
        return new StrategyVersion(1L, 1L, "Prueba", 1, factor, minimum, defaults, byPosition, null);
    }
}
