package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;

/**
 * Las métricas semanales de un jugador, antes de compararlas con sus pares (research D5).
 *
 * <p>{@code values} solo tiene las métricas que se pueden calcular: la ausencia significa "no se
 * puede calcular" y la métrica queda fuera de la comparación (FR-021). Las 15 de suma están
 * siempre, con 0 si el jugador no jugó (FR-022).</p>
 *
 * <p>{@link #of} es el único lugar que conoce los nombres de la 006 (data-model, sección 5.4): si
 * la 006 renombra un campo, cambia esta clase y su test.</p>
 */
public record WeeklyPerformance(Player player, Map<ValuationMetric, BigDecimal> values) {
    public static final int METRIC_SCALE = 6;

    private static final Map<ValuationMetric, Metric> SUMMED_COUNTS = summedCounts();
    private static final BigDecimal WIN = BigDecimal.ONE;
    private static final BigDecimal DRAW = new BigDecimal("0.5");
    private static final BigDecimal LOSS = BigDecimal.ZERO;

    public WeeklyPerformance {
        var copy = new EnumMap<ValuationMetric, BigDecimal>(ValuationMetric.class);
        copy.putAll(values);
        values = Collections.unmodifiableMap(copy);
    }

    /**
     * Arma las métricas de la semana con las estadísticas del jugador en sus partidos de esa
     * semana. Sin estadísticas, el jugador "no jugó".
     */
    public static WeeklyPerformance of(Player player, List<PlayerMatchStats> weekStats) {
        var values = new EnumMap<ValuationMetric, BigDecimal>(ValuationMetric.class);
        SUMMED_COUNTS.forEach((metric, count) -> values.put(metric, sum(weekStats, count)));
        values.put(ValuationMetric.MINUTES_PLAYED, BigDecimal.valueOf(totalMinutes(weekStats)));
        weightedRating(weekStats).ifPresent(rating -> values.put(ValuationMetric.RATING, rating));
        passAccuracy(weekStats).ifPresent(accuracy -> values.put(ValuationMetric.PASS_ACCURACY, accuracy));
        var resolvable = resolvableMatches(player, weekStats);
        if (!resolvable.isEmpty()) {
            values.put(ValuationMetric.TEAM_RESULT, teamResult(resolvable));
            values.put(ValuationMetric.CLEAN_SHEETS, cleanSheets(resolvable));
        }
        return new WeeklyPerformance(player, values);
    }

    /**
     * El valor de la métrica, o vacío si no se puede calcular.
     */
    public Optional<BigDecimal> value(ValuationMetric metric) {
        return Optional.ofNullable(values.get(metric));
    }

    private static BigDecimal sum(List<PlayerMatchStats> weekStats, Metric count) {
        return BigDecimal.valueOf(weekStats.stream().mapToLong(stats -> stats.counts().get(count)).sum());
    }

    private static long totalMinutes(List<PlayerMatchStats> weekStats) {
        return weekStats.stream().mapToLong(PlayerMatchStats::minutesPlayed).sum();
    }

    /**
     * {@code Σ(rating × minutos) / Σ minutos} sobre los partidos que jugó y que traen rating.
     */
    private static Optional<BigDecimal> weightedRating(List<PlayerMatchStats> weekStats) {
        var rated = weekStats.stream()
                .filter(stats -> played(stats) && stats.rating() != null)
                .toList();
        if (rated.isEmpty()) {
            return Optional.empty();
        }
        var weighted = rated.stream()
                .map(stats -> stats.rating().multiply(BigDecimal.valueOf(stats.minutesPlayed())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(divide(weighted, BigDecimal.valueOf(totalMinutes(rated))));
    }

    /**
     * {@code Σ pases precisos / Σ pases}; sin pases no se puede calcular.
     */
    private static Optional<BigDecimal> passAccuracy(List<PlayerMatchStats> weekStats) {
        var passes = sum(weekStats, Metric.PASSES);
        if (passes.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(divide(sum(weekStats, Metric.ACCURATE_PASSES), passes));
    }

    /**
     * Los partidos que jugó, con resultado, y en los que su equipo es el local o el visitante.
     */
    private static List<SidedMatch> resolvableMatches(Player player, List<PlayerMatchStats> weekStats) {
        return weekStats.stream()
                .filter(WeeklyPerformance::played)
                .map(stats -> SidedMatch.of(player, stats.match()))
                .flatMap(Optional::stream)
                .toList();
    }

    private static BigDecimal teamResult(List<SidedMatch> matches) {
        var points = matches.stream().map(SidedMatch::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(points, BigDecimal.valueOf(matches.size()));
    }

    private static BigDecimal cleanSheets(List<SidedMatch> matches) {
        return BigDecimal.valueOf(matches.stream().filter(SidedMatch::cleanSheet).count());
    }

    /**
     * "Jugó" es haber estado en la cancha al menos un minuto: un suplente que entró en el
     * descuento puede tener 0.
     */
    private static boolean played(PlayerMatchStats stats) {
        return stats.minutesPlayed() > 0;
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private static Map<ValuationMetric, Metric> summedCounts() {
        var counts = new EnumMap<ValuationMetric, Metric>(ValuationMetric.class);
        counts.put(ValuationMetric.GOALS, Metric.GOALS);
        counts.put(ValuationMetric.ASSISTS, Metric.ASSISTS);
        counts.put(ValuationMetric.SHOTS, Metric.SHOTS);
        counts.put(ValuationMetric.SHOTS_ON_TARGET, Metric.SHOTS_ON_TARGET);
        counts.put(ValuationMetric.KEY_PASSES, Metric.KEY_PASSES);
        counts.put(ValuationMetric.SUCCESSFUL_DRIBBLES, Metric.SUCCESSFUL_DRIBBLES);
        counts.put(ValuationMetric.TACKLES, Metric.TACKLES);
        counts.put(ValuationMetric.INTERCEPTIONS, Metric.INTERCEPTIONS);
        counts.put(ValuationMetric.CLEARANCES, Metric.CLEARANCES);
        counts.put(ValuationMetric.AERIALS_WON, Metric.AERIALS_WON);
        counts.put(ValuationMetric.PASSES, Metric.PASSES);
        counts.put(ValuationMetric.FOULS_COMMITTED, Metric.FOULS_COMMITTED);
        counts.put(ValuationMetric.YELLOW_CARDS, Metric.YELLOW_CARDS);
        counts.put(ValuationMetric.RED_CARDS, Metric.RED_CARDS);
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Un partido con el lado del jugador resuelto. El lado sale de comparar el equipo actual del
     * jugador con el local y el visitante (la 006 no guarda el equipo de cada partido).
     */
    private record SidedMatch(boolean home, MatchWinner winner, int goalsAgainst) {
        static Optional<SidedMatch> of(Player player, Match match) {
            var teamId = player.team().externalId();
            var home = match.homeTeam().externalId().equals(teamId);
            var away = match.awayTeam().externalId().equals(teamId);
            if (!home && !away || !hasResult(match)) {
                return Optional.empty();
            }
            var fullTime = match.fullTime();
            var goalsAgainst = home ? fullTime.away() : fullTime.home();
            return Optional.of(new SidedMatch(home, match.winner(), goalsAgainst));
        }

        BigDecimal points() {
            if (winner == MatchWinner.DRAW) {
                return DRAW;
            }
            var won = home ? winner == MatchWinner.HOME_TEAM : winner == MatchWinner.AWAY_TEAM;
            return won ? WIN : LOSS;
        }

        boolean cleanSheet() {
            return goalsAgainst == 0;
        }

        private static boolean hasResult(Match match) {
            Score fullTime = match.fullTime();
            return match.winner() != null && fullTime != null && fullTime.home() != null && fullTime.away() != null;
        }
    }
}
