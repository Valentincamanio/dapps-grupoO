package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

class MetricCountsTest {

    private static final String NEGATIVE_MESSAGE = "Una métrica no puede ser negativa.";

    @Test
    void unaMetricaQueFaltaValeCero() {
        MetricCounts counts = MetricCounts.of(Map.of(Metric.SHOTS, 4));

        int tackles = counts.get(Metric.TACKLES);

        assertThat(tackles).isZero();
    }

    @Test
    void rechazaUnValorNegativo() {
        Map<Metric, Integer> negative = Map.of(Metric.PASSES, -1);

        assertThatThrownBy(() -> MetricCounts.of(negative))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(NEGATIVE_MESSAGE);
    }

    @Test
    void withRechazaUnValorNegativo() {
        MetricCounts counts = MetricCounts.of(Map.of(Metric.PASSES, 30));

        assertThatThrownBy(() -> counts.with(Metric.GOALS, -1))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(NEGATIVE_MESSAGE);
    }

    @Test
    void ofCopiaElMapaYCambiarElOriginalNoLoAfecta() {
        Map<Metric, Integer> original = new EnumMap<>(Map.of(Metric.SHOTS, 4));
        MetricCounts counts = MetricCounts.of(original);

        original.put(Metric.SHOTS, 9);

        assertThat(counts.get(Metric.SHOTS)).isEqualTo(4);
    }

    @Test
    void withDevuelveUnaCopiaYNoCambiaElOriginal() {
        MetricCounts original = MetricCounts.of(Map.of(Metric.SHOTS, 4));

        MetricCounts changed = original.with(Metric.GOALS, 1);

        assertThat(changed.get(Metric.GOALS)).isEqualTo(1);
        assertThat(changed.get(Metric.SHOTS)).isEqualTo(4);
        assertThat(original.get(Metric.GOALS)).isZero();
    }

    @Test
    void dosValoresConLosMismosNumerosSonIgualesAunqueUnoTraigaElCeroExplicito() {
        MetricCounts withoutZero = MetricCounts.of(Map.of(Metric.SHOTS, 4));
        MetricCounts withZero = MetricCounts.of(Map.of(Metric.SHOTS, 4, Metric.GOALS, 0));

        boolean equal = withoutZero.equals(withZero);

        assertThat(equal).isTrue();
        assertThat(withoutZero).hasSameHashCodeAs(withZero);
    }
}
