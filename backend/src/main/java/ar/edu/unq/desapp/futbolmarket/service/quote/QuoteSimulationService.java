package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotingPlan;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulatedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulationResult;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeeklyQuotation;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidSimulationRequestException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * Simula una estrategia sin guardar nada (research D19, US7). Solo lee: no usa ningún método de
 * escritura de los repositories ni toma el candado de recálculo o el de sincronización.
 *
 * <p>Lee una sola vez los jugadores activos, los partidos terminados, todas las estadísticas y la
 * cotización actual de cada jugador, y reproduce en memoria, desde 100,00, todas las semanas que
 * hoy se cotizarían como si no hubiera nada cotizado, con {@link WeeklyQuotation#quoteWeek}: el
 * mismo código que el recálculo. Por eso simular la activa sobre un historial cotizado íntegramente
 * con su versión vigente da lo mismo que lo guardado (US7.7).</p>
 *
 * <p>No tiene estado: cada hoja vuelve a calcular todo. El orden es el del ranking (simulada
 * descendente, nombre e id), así dos simulaciones iguales dan lo mismo (SC-010).</p>
 */
@Service
@RequiredArgsConstructor
public class QuoteSimulationService {
    /**
     * El nombre de la versión que se arma con los parámetros enviados. No se informa ni se guarda.
     */
    static final String PARAMETERS_NAME = "Simulación";

    private static final Comparator<SimulatedQuote> RANKING_ORDER =
            Comparator.comparing(SimulatedQuote::simulatedValue, Comparator.reverseOrder())
                    .thenComparing(simulated -> simulated.player().name())
                    .thenComparing(simulated -> simulated.player().id());

    private final StrategyRepository strategyRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatsRepository statsRepository;
    private final QuoteRepository quoteRepository;
    private final Clock clock;

    /**
     * Los parámetros de valuación enviados en lugar de una estrategia guardada. Factor y mínimo
     * omitidos valen lo mismo que al crear una estrategia.
     */
    public record Parameters(BigDecimal scaleFactor, BigDecimal minimumValue, WeightSet defaultWeights,
                             Map<Position, WeightSet> positionWeights) {
    }

    /**
     * Simula una estrategia guardada (su versión más reciente) o los parámetros enviados; uno de
     * los dos, no ambos ni ninguno.
     */
    public SimulationResult simulate(Long strategyId, Parameters parameters, PlayerFilter filter, int page, int size) {
        if ((strategyId == null) == (parameters == null)) {
            throw new InvalidSimulationRequestException();
        }
        StrategyVersion strategy = strategyId != null
                ? strategyRepository.findCatalog().find(strategyId).latest()
                : versionOf(parameters);

        var activePlayers = playerRepository.findAllActive();
        var finishedMatches = matchRepository.findAllFinished();
        var statsByWeek = byWeek(statsRepository.findAll());
        var currentByPlayer = quoteRepository.findLatestByPlayer();

        var plan = QuotingPlan.of(Optional.empty(), QuotingPlan.firstWeekWithStats(finishedMatches), finishedMatches,
                clock.instant());
        var weeks = plan.weeksToQuote();
        var simulatedByPlayer = replay(weeks, strategy, activePlayers, statsByWeek);

        var rows = activePlayers.stream()
                .filter(player -> matches(player, filter))
                .map(player -> new SimulatedQuote(player, valueOf(currentByPlayer.get(player.id()), null),
                        valueOf(simulatedByPlayer.get(player.id()), Quote.INITIAL_VALUE)))
                .sorted(RANKING_ORDER)
                .toList();
        return new SimulationResult(pageOf(rows, page, size), strategyId != null ? strategy.ref() : null,
                weeks.size(), plan.pendingWeeks());
    }

    /**
     * Arma una versión con los parámetros: el modelo los valida con las mismas reglas que a una
     * estrategia (FR-057).
     */
    private static StrategyVersion versionOf(Parameters parameters) {
        return new StrategyVersion(null, null, PARAMETERS_NAME, 1,
                Objects.requireNonNullElse(parameters.scaleFactor(), StrategyVersion.DEFAULT_SCALE_FACTOR),
                Objects.requireNonNullElse(parameters.minimumValue(), StrategyVersion.DEFAULT_MINIMUM_VALUE),
                parameters.defaultWeights(), parameters.positionWeights(), null);
    }

    /**
     * Cotiza en memoria cada semana, en orden, con la base de la semana anterior. Devuelve por id
     * de jugador su última calculada.
     */
    private static Map<Long, Quote> replay(List<QuoteWeek> weeks, StrategyVersion strategy, List<Player> activePlayers,
                                           Map<QuoteWeek, List<PlayerMatchStats>> statsByWeek) {
        var last = new HashMap<Long, Quote>();
        for (QuoteWeek week : weeks) {
            WeeklyQuotation.quoteWeek(week, strategy, activePlayers, statsByWeek.getOrDefault(week, List.of()), last)
                    .stream()
                    .filter(quote -> quote.type() == QuoteType.CALCULATED)
                    .forEach(quote -> last.put(quote.playerId(), quote));
        }
        return last;
    }

    /**
     * Las estadísticas agrupadas por semana una sola vez, para no recorrerlas todas en cada semana.
     */
    private static Map<QuoteWeek, List<PlayerMatchStats>> byWeek(List<PlayerMatchStats> stats) {
        return stats.stream().collect(Collectors.groupingBy(s -> QuoteWeek.containing(s.match().utcDate())));
    }

    /**
     * Los filtros del catálogo: los nulos no filtran y el equipo es el nombre oficial exacto.
     */
    private static boolean matches(Player player, PlayerFilter filter) {
        return (filter.league() == null || filter.league() == player.league())
                && (filter.team() == null || filter.team().equals(player.team().name()))
                && (filter.position() == null || filter.position() == player.position());
    }

    private static BigDecimal valueOf(Quote quote, BigDecimal orElse) {
        return quote == null ? orElse : quote.value();
    }

    private static QuotePage<SimulatedQuote> pageOf(List<SimulatedQuote> rows, int page, int size) {
        long from = Math.min((long) page * size, rows.size());
        long to = Math.min(from + size, rows.size());
        return new QuotePage<>(rows.subList((int) from, (int) to), page, size, rows.size());
    }
}
