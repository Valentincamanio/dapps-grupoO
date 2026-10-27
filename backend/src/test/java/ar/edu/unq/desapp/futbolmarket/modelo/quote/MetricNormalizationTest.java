package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.MetricDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class MetricNormalizationTest {

    @Test
    void cincoDelanterosConCeroCeroUnoDosYTresGolesDanElEjemploDelSpec() {
        var goals = Map.of(1L, n("0"), 2L, n("0"), 3L, n("1"), 4L, n("2"), 5L, n("3"));

        var normalized = MetricNormalization.normalize(goals, MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).containsOnly(
                entry(1L, n("-0.600000")),
                entry(2L, n("-0.600000")),
                entry(3L, n("0.000000")),
                entry(4L, n("0.400000")),
                entry(5L, n("0.800000")));
        assertThat(normalized.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isZero();
    }

    @Test
    void siTodosEmpatanLaMetricaEsCeroParaTodos() {
        var tackles = Map.of(1L, n("2"), 2L, n("2"), 3L, n("2"));

        var normalized = MetricNormalization.normalize(tackles, MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).containsOnly(
                entry(1L, n("0.000000")), entry(2L, n("0.000000")), entry(3L, n("0.000000")));
    }

    @Test
    void conUnSoloParLaMetricaEsCero() {
        var normalized = MetricNormalization.normalize(Map.of(7L, n("5")), MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).containsOnly(entry(7L, n("0.000000")));
    }

    @Test
    void enUnaMetricaInvertidaElQueTuvoMenosQuedaMejor() {
        var fouls = Map.of(1L, n("0"), 2L, n("1"), 3L, n("2"));

        var normalized = MetricNormalization.normalize(fouls, MetricDirection.LOWER_IS_BETTER);

        assertThat(normalized).containsOnly(
                entry(1L, n("0.666666")),
                entry(2L, n("0.000000")),
                entry(3L, n("-0.666666")));
    }

    @Test
    void laMetricaSaleDeLaPosicionRelativaYaRedondeada() {
        var shots = Map.of(1L, n("0"), 2L, n("1"), 3L, n("2"));

        var normalized = MetricNormalization.normalize(shots, MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized.get(1L)).isEqualTo(n("-0.666666"));
        assertThat(normalized.get(3L)).isEqualTo(n("0.666666"));
    }

    @Test
    void valoresIgualesConDistintaEscalaEmpatan() {
        var ratings = Map.of(1L, n("7"), 2L, n("7.000000"));

        var normalized = MetricNormalization.normalize(ratings, MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).containsOnly(entry(1L, n("0.000000")), entry(2L, n("0.000000")));
    }

    @Test
    void losJugadoresSinLaMetricaNoFiguranEnLaSalida() {
        var ratings = Map.of(1L, n("6.5"), 2L, n("8"));

        var normalized = MetricNormalization.normalize(ratings, MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).containsOnlyKeys(1L, 2L);
        assertThat(normalized).containsOnly(entry(1L, n("-0.500000")), entry(2L, n("0.500000")));
    }

    @Test
    void unGrupoVacioNoDevuelveNada() {
        var normalized = MetricNormalization.normalize(Map.of(), MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).isEmpty();
    }

    @Test
    void unValorNuloEsUnErrorDeProgramacion() {
        var values = new HashMap<Long, BigDecimal>();
        values.put(1L, null);

        assertThatThrownBy(() -> MetricNormalization.normalize(values, MetricDirection.HIGHER_IS_BETTER))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void elResultadoNoSePuedeModificar() {
        var normalized = MetricNormalization.normalize(Map.of(1L, n("1")), MetricDirection.HIGHER_IS_BETTER);

        assertThat(normalized).isUnmodifiable();
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }
}
