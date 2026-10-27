package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StartingStrategiesTest {
    private static final EnumSet<ValuationMetric> DISCIPLINE = EnumSet.of(
            ValuationMetric.FOULS_COMMITTED, ValuationMetric.YELLOW_CARDS, ValuationMetric.RED_CARDS);
    private static final EnumSet<ValuationMetric> CONTEXT = EnumSet.of(
            ValuationMetric.TEAM_RESULT, ValuationMetric.CLEAN_SHEETS);

    @Test
    void laEstrategiaASumaUnoYPesaIgualEnTodasLasPosiciones() {
        var a = version(StartingStrategies.MATCH_PERFORMANCE);

        assertThat(sum(a.defaultWeights())).isEqualByComparingTo("1.00");
        assertThat(Arrays.stream(Position.values()).map(a::weightsFor).distinct()).hasSize(1);
        assertThat(a.weightsFor(Position.FORWARD).weights()).containsExactlyInAnyOrderEntriesOf(Map.of(
                ValuationMetric.GOALS, new BigDecimal("0.25"),
                ValuationMetric.ASSISTS, new BigDecimal("0.15"),
                ValuationMetric.SHOTS, new BigDecimal("0.10"),
                ValuationMetric.KEY_PASSES, new BigDecimal("0.10"),
                ValuationMetric.SUCCESSFUL_DRIBBLES, new BigDecimal("0.10"),
                ValuationMetric.TACKLES, new BigDecimal("0.10"),
                ValuationMetric.RATING, new BigDecimal("0.20")));
    }

    @Test
    void lasDosUsanElFactorYElMinimoPorDefecto() {
        assertThat(StartingStrategies.all()).allSatisfy(definition -> {
            var version = version(definition);
            assertThat(version.scaleFactor()).isEqualByComparingTo("10");
            assertThat(version.minimumValue()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void laEstrategiaBSumaUnoEnCadaPosicionYEnElJuegoPorDefecto() {
        var b = version(StartingStrategies.BALANCED_BY_POSITION);

        assertThat(sum(b.defaultWeights())).isEqualByComparingTo("1.00");
        assertThat(b.positionWeights()).containsOnlyKeys(Position.values());
        assertThat(b.positionWeights().values()).allSatisfy(weights ->
                assertThat(sum(weights)).isEqualByComparingTo("1.00"));
    }

    @Test
    void elJuegoPorDefectoDeBEsElDeMediocampista() {
        var b = version(StartingStrategies.BALANCED_BY_POSITION);

        assertThat(b.defaultWeights()).isEqualTo(b.weightsFor(Position.MIDFIELDER));
    }

    @Test
    void laEstrategiaBDifiereEnAlMenosDosPosiciones() {
        var b = version(StartingStrategies.BALANCED_BY_POSITION);

        var distinct = Arrays.stream(Position.values()).map(b::weightsFor).distinct().count();

        assertThat(distinct).isGreaterThanOrEqualTo(2);
    }

    @Test
    void laEstrategiaBIncluyeUnaMetricaDeDisciplinaYUnaDeContexto() {
        var b = version(StartingStrategies.BALANCED_BY_POSITION);

        var used = EnumSet.noneOf(ValuationMetric.class);
        Arrays.stream(Position.values()).forEach(position -> used.addAll(b.usedMetrics(position)));

        assertThat(used).containsAnyElementsOf(DISCIPLINE);
        assertThat(used).containsAnyElementsOf(CONTEXT);
    }

    @Test
    void sembrarLasDosEnUnCatalogoVacioDejaActivaLaA() {
        var catalog = StrategyCatalog.empty();
        for (var definition : StartingStrategies.all()) {
            catalog = catalog.create(definition.name(), definition.scaleFactor(), definition.minimumValue(),
                    definition.defaultWeights(), definition.positionWeights()).catalog();
        }

        assertThat(catalog.strategies()).extracting(Strategy::name).containsExactly(
                "Rendimiento de partido", "Equilibrada por posición");
        assertThat(catalog.activeVersion().name()).isEqualTo(StartingStrategies.MATCH_PERFORMANCE_NAME);
    }

    private static StrategyVersion version(StartingStrategies.Definition definition) {
        return new StrategyVersion(null, null, definition.name(), 1, definition.scaleFactor(),
                definition.minimumValue(), definition.defaultWeights(), definition.positionWeights(), null);
    }

    private static BigDecimal sum(WeightSet weights) {
        return List.copyOf(weights.weights().values()).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
