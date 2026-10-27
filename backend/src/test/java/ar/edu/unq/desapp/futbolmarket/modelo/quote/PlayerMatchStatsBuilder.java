package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Arma el rendimiento de un jugador en un partido con todo en 0, sin rating, y solo lo que el test
 * le indica (data-model, sección 5.5).
 *
 * <p>Es el único test de la feature que conoce el constructor de {@link PlayerMatchStats} de la
 * 006: si cambia, se arregla acá. Por defecto el jugador es {@link QuoteFixtures#NDIAYE} y el
 * partido, {@link QuoteFixtures#EVERTON_MAN_UTD}.</p>
 */
public final class PlayerMatchStatsBuilder {
    private Player player = QuoteFixtures.NDIAYE;
    private Match match = QuoteFixtures.EVERTON_MAN_UTD;
    private boolean starter;
    private int minutesPlayed;
    private final Map<Metric, Integer> counts = new EnumMap<>(Metric.class);
    private BigDecimal rating;

    private PlayerMatchStatsBuilder() {
    }

    public static PlayerMatchStatsBuilder stats() {
        return new PlayerMatchStatsBuilder();
    }

    public PlayerMatchStatsBuilder forPlayer(Player player) {
        this.player = player;
        return this;
    }

    public PlayerMatchStatsBuilder inMatch(Match match) {
        this.match = match;
        return this;
    }

    public PlayerMatchStatsBuilder starter() {
        this.starter = true;
        return this;
    }

    public PlayerMatchStatsBuilder minutes(int minutes) {
        this.minutesPlayed = minutes;
        return this;
    }

    public PlayerMatchStatsBuilder rating(String rating) {
        this.rating = new BigDecimal(rating);
        return this;
    }

    public PlayerMatchStatsBuilder with(Metric metric, int value) {
        counts.put(metric, value);
        return this;
    }

    public PlayerMatchStatsBuilder goals(int goals) {
        return with(Metric.GOALS, goals);
    }

    public PlayerMatchStatsBuilder assists(int assists) {
        return with(Metric.ASSISTS, assists);
    }

    public PlayerMatchStatsBuilder shots(int shots) {
        return with(Metric.SHOTS, shots);
    }

    public PlayerMatchStatsBuilder shotsOnTarget(int shotsOnTarget) {
        return with(Metric.SHOTS_ON_TARGET, shotsOnTarget);
    }

    public PlayerMatchStatsBuilder keyPasses(int keyPasses) {
        return with(Metric.KEY_PASSES, keyPasses);
    }

    public PlayerMatchStatsBuilder passes(int passes, int accuratePasses) {
        return with(Metric.PASSES, passes).with(Metric.ACCURATE_PASSES, accuratePasses);
    }

    public PlayerMatchStatsBuilder successfulDribbles(int dribbles) {
        return with(Metric.SUCCESSFUL_DRIBBLES, dribbles);
    }

    public PlayerMatchStatsBuilder tackles(int tackles) {
        return with(Metric.TACKLES, tackles);
    }

    public PlayerMatchStatsBuilder interceptions(int interceptions) {
        return with(Metric.INTERCEPTIONS, interceptions);
    }

    public PlayerMatchStatsBuilder clearances(int clearances) {
        return with(Metric.CLEARANCES, clearances);
    }

    public PlayerMatchStatsBuilder aerialsWon(int aerialsWon) {
        return with(Metric.AERIALS_WON, aerialsWon);
    }

    public PlayerMatchStatsBuilder foulsCommitted(int fouls) {
        return with(Metric.FOULS_COMMITTED, fouls);
    }

    public PlayerMatchStatsBuilder yellowCards(int yellowCards) {
        return with(Metric.YELLOW_CARDS, yellowCards);
    }

    public PlayerMatchStatsBuilder redCards(int redCards) {
        return with(Metric.RED_CARDS, redCards);
    }

    public PlayerMatchStats build() {
        return new PlayerMatchStats(null, player, match, starter, minutesPlayed, MetricCounts.of(counts), rating,
                false);
    }
}
