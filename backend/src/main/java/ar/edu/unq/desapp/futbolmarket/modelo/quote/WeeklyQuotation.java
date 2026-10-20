package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;

/**
 * El caso de uso del modelo: dada una semana, una estrategia y los datos ya leídos, decide qué
 * cotizaciones nacen (data-model, sección 3.5). No lee ni guarda nada.
 *
 * <p>Los pares de un jugador son los activos de su misma posición, de las cinco ligas juntas
 * (research D6). El resultado no depende del orden de las listas que recibe (FR-026): los
 * jugadores se recorren por id y las sumas son exactas.</p>
 */
public final class WeeklyQuotation {
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(Quote.SCORE_SCALE);

    private WeeklyQuotation() {
    }

    /**
     * Las cotizaciones del cierre de la semana, por jugador activo y en orden de id: la
     * {@code INITIAL} de 100,00 si el jugador no tenía ninguna (rige desde el inicio de la semana)
     * y la {@code CALCULATED}, que rige desde el cierre (research D10).
     *
     * @param lastBeforeWeek por id de jugador, su última cotización de las semanas anteriores
     *                       (incluidas las recotizadas, research D9). Quien no figura no tiene
     *                       ninguna y parte de 100,00.
     */
    public static List<Quote> quoteWeek(QuoteWeek week, StrategyVersion strategy, List<Player> activePlayers,
                                        List<PlayerMatchStats> weekStats, Map<Long, Quote> lastBeforeWeek) {
        var quotes = new ArrayList<Quote>();
        weekScores(week, strategy, activePlayers, weekStats).forEach((playerId, score) -> {
            var previous = lastBeforeWeek.get(playerId);
            if (previous == null) {
                quotes.add(initial(playerId, week, strategy));
            }
            var baseValue = previous == null ? Quote.INITIAL_VALUE : previous.value();
            quotes.add(new Quote(null, playerId, week, week.end(), strategy.valueFor(baseValue, score), baseValue,
                    score, QuoteType.CALCULATED, strategy.ref()));
        });
        return List.copyOf(quotes);
    }

    /**
     * El score de la semana de cada jugador activo, ordenado por id. Es el cálculo que comparten
     * el cierre semanal, la recotización y la simulación, para que los tres den lo mismo.
     *
     * <p>Solo cuentan las estadísticas de partidos de esa semana y de jugadores activos. Se
     * normalizan solo las métricas con peso en cada posición: las otras no entran en el score.</p>
     */
    public static SortedMap<Long, BigDecimal> weekScores(QuoteWeek week, StrategyVersion strategy,
                                                         List<Player> activePlayers,
                                                         List<PlayerMatchStats> weekStats) {
        var players = activeById(activePlayers);
        var statsByPlayer = statsByPlayer(week, weekStats, players);
        var scores = new TreeMap<Long, BigDecimal>();
        peersByPosition(players).forEach((position, peers) ->
                scores.putAll(positionScores(position, peers, statsByPlayer, strategy)));
        return Collections.unmodifiableSortedMap(scores);
    }

    private static Map<Long, BigDecimal> positionScores(Position position, List<Player> peers,
                                                        Map<Long, List<PlayerMatchStats>> statsByPlayer,
                                                        StrategyVersion strategy) {
        var performances = new TreeMap<Long, WeeklyPerformance>();
        peers.forEach(player -> performances.put(player.id(),
                WeeklyPerformance.of(player, statsByPlayer.getOrDefault(player.id(), List.of()))));
        var normalizedByPlayer = new TreeMap<Long, Map<ValuationMetric, BigDecimal>>();
        performances.keySet().forEach(id -> normalizedByPlayer.put(id, new EnumMap<>(ValuationMetric.class)));
        for (var metric : strategy.usedMetrics(position)) {
            MetricNormalization.normalize(valuesOf(metric, performances), metric.direction())
                    .forEach((id, normalized) -> normalizedByPlayer.get(id).put(metric, normalized));
        }
        var scores = new TreeMap<Long, BigDecimal>();
        normalizedByPlayer.forEach((id, normalized) -> scores.put(id, strategy.scoreFor(position, normalized)));
        return scores;
    }

    /**
     * El grupo de comparación de una métrica: los pares que la tienen (FR-021).
     */
    private static Map<Long, BigDecimal> valuesOf(ValuationMetric metric, Map<Long, WeeklyPerformance> performances) {
        var values = new HashMap<Long, BigDecimal>();
        performances.forEach((id, performance) -> performance.value(metric).ifPresent(value -> values.put(id, value)));
        return values;
    }

    private static Quote initial(long playerId, QuoteWeek week, StrategyVersion strategy) {
        return new Quote(null, playerId, week, week.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategy.ref());
    }

    private static SortedMap<Long, Player> activeById(List<Player> activePlayers) {
        var players = new TreeMap<Long, Player>();
        activePlayers.stream().filter(Player::active).forEach(player -> players.put(player.id(), player));
        return players;
    }

    private static Map<Long, List<PlayerMatchStats>> statsByPlayer(QuoteWeek week, List<PlayerMatchStats> weekStats,
                                                                   Map<Long, Player> players) {
        var statsByPlayer = new HashMap<Long, List<PlayerMatchStats>>();
        weekStats.stream()
                .filter(stats -> week.equals(QuoteWeek.containing(stats.match().utcDate())))
                .filter(stats -> players.containsKey(stats.player().id()))
                .forEach(stats -> statsByPlayer.computeIfAbsent(stats.player().id(), id -> new ArrayList<>()).add(stats));
        return statsByPlayer;
    }

    private static Map<Position, List<Player>> peersByPosition(SortedMap<Long, Player> players) {
        var peers = new EnumMap<Position, List<Player>>(Position.class);
        players.values().forEach(player -> peers.computeIfAbsent(player.position(), position -> new ArrayList<>())
                .add(player));
        return peers;
    }
}
