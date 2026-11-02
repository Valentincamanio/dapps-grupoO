package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.BETO;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON_MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.MAGUIRE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.SAKA;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.TARKOWSKI;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;

/**
 * El cierre de una semana con potenciados: el factor de escala del potenciado se multiplica por su
 * porcentaje y nada más cambia. Solo pesan los goles, con factor 10: Ndiaye (1 gol) y Beto (0) son
 * los delanteros, Tarkowski (2) y Maguire (0) los defensores. Saka está potenciado pero no está
 * activo.
 */
class WeeklyQuotationBoostTest {
    private static final BigDecimal SCALE_FACTOR = BigDecimal.TEN;
    private static final StrategyVersion GOALS = goalsStrategy(1L, "10.00");
    private static final List<Player> ACTIVE = List.of(NDIAYE, BETO, TARKOWSKI, MAGUIRE);
    private static final WeekBoosts BOOSTS = new WeekBoosts(WEEK, List.of(
            new PlayerBoost(NDIAYE.id(), 300), new PlayerBoost(BETO.id(), 200),
            new PlayerBoost(TARKOWSKI.id(), 250), new PlayerBoost(SAKA.id(), 400)));

    @Test
    void elPotenciadoLlevaSuFactorDeEscalaMultiplicadoYElScoreNoCambia() {
        var plain = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of()));

        var boosted = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of(), BOOSTS));

        assertThat(boosted.get(NDIAYE.id()).value()).isEqualTo(expected(boosted.get(NDIAYE.id()), "3"));
        assertThat(boosted.get(BETO.id()).value()).isEqualTo(expected(boosted.get(BETO.id()), "2"));
        assertThat(boosted.get(TARKOWSKI.id()).value()).isEqualTo(expected(boosted.get(TARKOWSKI.id()), "2.5"));
        assertThat(boosted.get(NDIAYE.id()).value()).isGreaterThan(plain.get(NDIAYE.id()).value());
        assertThat(boosted.get(BETO.id()).value()).isLessThan(plain.get(BETO.id()).value());
        assertThat(boosted).allSatisfy((id, quote) -> assertThat(quote.score()).isEqualTo(plain.get(id).score()));
    }

    @Test
    void elQueNoEstaPotenciadoCotizaIgualQueSinPotenciados() {
        var plain = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of()));

        var boosted = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of(), BOOSTS));

        assertThat(boosted.get(MAGUIRE.id())).isEqualTo(plain.get(MAGUIRE.id()));
        assertThat(boosted).doesNotContainKey(SAKA.id());
    }

    @Test
    void sinPotenciadosDaLoMismoQueAntes() {
        var plain = WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of());

        var none = WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of(), WeekBoosts.none(WEEK));

        assertThat(none).isEqualTo(plain);
    }

    @Test
    void elMinimoDeLaEstrategiaSigueRigiendoParaUnPotenciado() {
        var highMinimum = goalsStrategy(2L, "92.00");

        var boosted = calculatedById(WeeklyQuotation.quoteWeek(WEEK, highMinimum, ACTIVE, weekStats(), Map.of(),
                BOOSTS));

        assertThat(boosted.get(BETO.id()).score()).isNegative();
        assertThat(boosted.get(BETO.id()).value()).isEqualTo(new BigDecimal("92.00"));
    }

    /**
     * Se recotiza la semana cerrada un miércoles de la siguiente: rigen los potenciados de esa
     * semana siguiente (Beto ×4, Maguire ×3), no los de la recotizada (Ndiaye ×3).
     */
    @Test
    void laRecotizadaLlevaElMultiplicadorDeLosPotenciadosDeLaSemanaEnCurso() {
        Map<Long, Quote> latestOfWeek = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(),
                Map.of(), BOOSTS));
        var newStrategy = goalsStrategy(3L, "10.00");
        Instant now = WEEK.end().plus(Duration.ofDays(2));
        var currentBoosts = new WeekBoosts(WEEK.next(), List.of(
                new PlayerBoost(BETO.id(), 400), new PlayerBoost(MAGUIRE.id(), 300),
                new PlayerBoost(TARKOWSKI.id(), 200), new PlayerBoost(SAKA.id(), 200)));

        var requoted = calculatedById(WeeklyQuotation.requoteWeek(WEEK, newStrategy, ACTIVE, weekStats(), Map.of(),
                latestOfWeek, now, currentBoosts));

        assertThat(requoted.values()).allSatisfy(quote -> assertThat(quote.type()).isEqualTo(QuoteType.REQUOTED));
        assertThat(requoted.get(BETO.id()).value()).isEqualTo(expected(requoted.get(BETO.id()), "4"));
        assertThat(requoted.get(MAGUIRE.id()).value()).isEqualTo(expected(requoted.get(MAGUIRE.id()), "3"));
        assertThat(requoted.get(NDIAYE.id()).value()).isEqualTo(expected(requoted.get(NDIAYE.id()), "1"));
    }

    @Test
    void recotizarConLosPotenciadosDeLaSemanaRecotizadaYNoDeLaEnCursoSeRechaza() {
        Map<Long, Quote> latestOfWeek = calculatedById(WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(),
                Map.of()));
        Instant now = WEEK.end().plus(Duration.ofDays(2));

        assertThatThrownBy(() -> WeeklyQuotation.requoteWeek(WEEK, goalsStrategy(3L, "10.00"), ACTIVE, weekStats(),
                Map.of(), latestOfWeek, now, BOOSTS))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void losPotenciadosDeOtraSemanaSeRechazan() {
        var otherWeek = WeekBoosts.none(WEEK.next());

        assertThatThrownBy(() -> WeeklyQuotation.quoteWeek(WEEK, GOALS, ACTIVE, weekStats(), Map.of(), otherWeek))
                .isInstanceOf(QuoteInvariantException.class);
    }

    /**
     * {@code max(mínimo, 100 + score × 10 × multiplicador)}, redondeado una vez a 2 decimales.
     */
    private static BigDecimal expected(Quote quote, String multiplier) {
        return Quote.INITIAL_VALUE.add(quote.score().multiply(SCALE_FACTOR).multiply(new BigDecimal(multiplier)))
                .max(GOALS.minimumValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static List<PlayerMatchStats> weekStats() {
        return List.of(
                stats().forPlayer(NDIAYE).inMatch(EVERTON_MAN_UTD).minutes(90).goals(1).build(),
                stats().forPlayer(BETO).inMatch(EVERTON_MAN_UTD).minutes(90).build(),
                stats().forPlayer(TARKOWSKI).inMatch(EVERTON_MAN_UTD).minutes(90).goals(2).build(),
                stats().forPlayer(MAGUIRE).inMatch(EVERTON_MAN_UTD).minutes(90).build());
    }

    /**
     * Las calculadas y las recotizadas por id de jugador, sin las iniciales.
     */
    private static Map<Long, Quote> calculatedById(List<Quote> quotes) {
        return quotes.stream()
                .filter(quote -> quote.type() != QuoteType.INITIAL)
                .collect(Collectors.toMap(Quote::playerId, Function.identity()));
    }

    private static StrategyVersion goalsStrategy(long id, String minimum) {
        return new StrategyVersion(id, id, "Goles " + id, 1, SCALE_FACTOR, new BigDecimal(minimum),
                new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE)), Map.of(), null);
    }
}
