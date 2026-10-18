package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class PlayerMatchStatsTest {

    private static final Season SEASON = new Season(
            1L, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8);
    private static final Team EVERTON = new Team(1L, "62", "Everton FC", null, League.PREMIER, "31");
    private static final Team MAN_UTD = new Team(2L, "66", "Manchester United FC", null, League.PREMIER, "32");
    private static final Match MATCH = new Match(1L, "560600", SEASON, Instant.parse("2026-10-17T14:00:00Z"), 8,
            MatchStatus.FINISHED, EVERTON, MAN_UTD, null, null, null, "1983600", false);
    private static final Player NDIAYE = new Player(1L, "8135", "Iliman Ndiaye", Position.FORWARD, EVERTON);
    private static final MetricCounts COUNTS = MetricCounts.of(Map.of(Metric.GOALS, 1, Metric.SHOTS, 4));
    private static final String RATING_MESSAGE = "El rating tiene que estar entre 0 y 10, con a lo sumo dos decimales.";

    @Test
    void rechazaUnaEstadisticaSinJugador() {
        assertThatThrownBy(() -> new PlayerMatchStats(null, null, MATCH, true, 96, COUNTS, null, false))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El jugador de la estadística es obligatorio.");
    }

    @Test
    void rechazaUnaEstadisticaSinPartido() {
        assertThatThrownBy(() -> new PlayerMatchStats(null, NDIAYE, null, true, 96, COUNTS, null, false))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El partido de la estadística es obligatorio.");
    }

    @Test
    void rechazaMinutosJugadosNegativos() {
        assertThatThrownBy(() -> new PlayerMatchStats(null, NDIAYE, MATCH, true, -1, COUNTS, null, false))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("Los minutos jugados no pueden ser negativos.");
    }

    @Test
    void aceptaCeroMinutosJugados() {
        PlayerMatchStats stats = new PlayerMatchStats(null, NDIAYE, MATCH, false, 0, COUNTS, null, false);

        assertThat(stats.minutesPlayed()).isZero();
    }

    @Test
    void aceptaUnaEstadisticaSinRating() {
        PlayerMatchStats stats = stats(null);

        assertThat(stats.rating()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "7.65", "6.00"})
    void aceptaUnRatingEntreCeroYDiezConHastaDosDecimales(String value) {
        BigDecimal rating = new BigDecimal(value);

        PlayerMatchStats stats = stats(rating);

        assertThat(stats.rating()).isEqualByComparingTo(rating);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "10.01", "7.655"})
    void rechazaUnRatingFueraDeRangoOConMasDeDosDecimales(String value) {
        BigDecimal rating = new BigDecimal(value);

        assertThatThrownBy(() -> stats(rating))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(RATING_MESSAGE);
    }

    @Test
    void elRatingQuedaConDosDecimales() {
        PlayerMatchStats stats = stats(new BigDecimal("6"));

        BigDecimal rating = stats.rating();

        assertThat(rating).isEqualTo(new BigDecimal("6.00"));
    }

    @Test
    void sinMetricasQuedaConTodasEnCero() {
        PlayerMatchStats stats = new PlayerMatchStats(null, NDIAYE, MATCH, true, 96, null, null, false);

        MetricCounts counts = stats.counts();

        assertThat(counts).isEqualTo(MetricCounts.empty());
    }

    private static PlayerMatchStats stats(BigDecimal rating) {
        return new PlayerMatchStats(null, NDIAYE, MATCH, true, 96, COUNTS, rating, true);
    }
}
