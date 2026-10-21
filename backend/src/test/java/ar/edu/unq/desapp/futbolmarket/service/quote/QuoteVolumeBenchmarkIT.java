package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulationResult;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * La medición de volumen de research.md (D15): 2.600 jugadores y 40 semanas de estadísticas
 * sintéticas en una H2 en archivo, la reconstrucción completa, una semana suelta, la simulación y
 * las consultas, contra los topes de SC-008, SC-009 y SC-010.
 *
 * <p>Tarda minutos, por eso solo corre con {@code QUOTES_BENCHMARK=true} y queda fuera del build
 * normal (quickstart.md, escenario G). Las estadísticas salen de un {@link Random} con semilla fija:
 * dos corridas cargan lo mismo.</p>
 *
 * <p>La semana suelta se mide como el lunes de una semana normal: se borran las cotizaciones de la
 * última semana y se vuelve a cotizar solo esa, con la base de la anterior.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:file:./build/benchmark/quotes-volume")
@EnabledIfEnvironmentVariable(named = "QUOTES_BENCHMARK", matches = "true")
class QuoteVolumeBenchmarkIT {
    private static final Logger log = LoggerFactory.getLogger(QuoteVolumeBenchmarkIT.class);

    private static final Path DATABASE_DIRECTORY = Path.of("build", "benchmark");
    private static final int TEAMS = 130;
    private static final int PLAYERS_PER_TEAM = 20;
    private static final int PLAYERS = TEAMS * PLAYERS_PER_TEAM;
    private static final int WEEKS = 40;
    private static final long SEED = 7L;
    private static final int PAGE_SIZE = 20;
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);

    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek LAST = weekAfter(FIRST, WEEKS - 1);
    private static final Instant NOW = LAST.next().start().plus(Duration.ofHours(9));

    private static final Duration ONE_WEEK_LIMIT = Duration.ofMinutes(1);
    private static final Duration SEASON_LIMIT = Duration.ofMinutes(5);
    private static final Duration SIMULATION_LIMIT = Duration.ofSeconds(30);
    private static final Duration QUERY_LIMIT = Duration.ofSeconds(2);

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private QuoteSimulationService simulationService;

    @Autowired
    private QuoteQueryService queryService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private ApplicationContext context;

    private final Random random = new Random(SEED);

    /**
     * La base de archivo de una corrida anterior se borra antes de levantar el contexto, así el
     * tamaño medido es el de esta carga.
     */
    @BeforeAll
    static void deletePreviousDatabase() throws IOException {
        if (Files.exists(DATABASE_DIRECTORY)) {
            try (Stream<Path> files = Files.walk(DATABASE_DIRECTORY)) {
                files.sorted(Collections.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void elCatalogoCompletoSeCotizaYSeConsultaDentroDeLosTopesDelSpec() throws IOException {
        var database = new QuoteDatabase(context);
        database.clean();
        long loadStart = System.nanoTime();
        List<Player> players = loadSeason(database);
        log.info("Carga: {} jugadores y {} semanas de estadísticas en {}", players.size(), WEEKS,
                Duration.ofNanos(System.nanoTime() - loadStart));

        Timed<RecalculationReport> season = timed(() -> recalculationService.recalculate(RecalculationOrigin.MANUAL));
        long rows = database.quoteCount();

        database.quotesOf(LAST).forEach(database::deleteQuote);
        Timed<RecalculationReport> oneWeek = timed(() -> recalculationService.recalculate(RecalculationOrigin.MANUAL));

        Long activeId = strategyRepository.findCatalog().activeStrategy().id();
        Timed<SimulationResult> simulation = timed(() -> simulationService.simulate(activeId, null, NO_FILTER, 0,
                PAGE_SIZE));
        Timed<RankingPage> ranking = timed(() -> queryService.getRanking(NO_FILTER, 0, PAGE_SIZE));
        Long playerId = players.get(PLAYERS / 2).id();
        Timed<?> playerQuote = timed(() -> queryService.getCurrent(playerId));

        log.info("Reconstrucción de {} semanas: {}", season.value().quotedWeeks().size(), season.duration());
        log.info("Una semana ({} cotizaciones): {}", oneWeek.value().quotedWeeks().getFirst().calculated(),
                oneWeek.duration());
        log.info("Filas en quotes: {}", rows);
        log.info("Tamaño de la base: {} MB", databaseSize() / (1024 * 1024));
        log.info("Primera página de la simulación ({} semanas): {}", simulation.value().weeksSimulated(),
                simulation.duration());
        log.info("Página del ranking: {}", ranking.duration());
        log.info("Cotización actual de un jugador: {}", playerQuote.duration());

        assertThat(season.value().quotedWeeks()).hasSize(WEEKS);
        assertThat(season.value().pendingWeeks()).isEmpty();
        assertThat(rows).isEqualTo((long) PLAYERS * (WEEKS + 1));
        assertThat(oneWeek.value().quotedWeeks()).singleElement()
                .satisfies(week -> assertThat(week.week()).isEqualTo(LAST));
        assertThat(database.quoteCount()).isEqualTo(rows);
        assertThat(simulation.value().page().content()).hasSize(PAGE_SIZE);
        assertThat(ranking.value().page().content()).hasSize(PAGE_SIZE);

        assertThat(season.duration()).isLessThan(SEASON_LIMIT);
        assertThat(oneWeek.duration()).isLessThan(ONE_WEEK_LIMIT);
        assertThat(simulation.duration()).isLessThan(SIMULATION_LIMIT);
        assertThat(ranking.duration()).isLessThan(QUERY_LIMIT);
        assertThat(playerQuote.duration()).isLessThan(QUERY_LIMIT);
    }

    /**
     * 130 equipos de 20 jugadores (las cuatro posiciones repartidas) y, cada semana, 65 partidos
     * con los equipos sorteados de a pares, todos con estadísticas.
     */
    private List<Player> loadSeason(QuoteDatabase database) {
        List<Team> teams = new ArrayList<>();
        List<List<Player>> squads = new ArrayList<>();
        Position[] positions = Position.values();
        for (int t = 0; t < TEAMS; t++) {
            Team team = database.team(String.valueOf(10_000 + t), "Equipo " + t);
            List<Player> squad = new ArrayList<>();
            for (int p = 0; p < PLAYERS_PER_TEAM; p++) {
                int number = t * PLAYERS_PER_TEAM + p;
                squad.add(database.player(String.valueOf(100_000 + number), "Jugador " + number,
                        positions[p % positions.length], team));
            }
            teams.add(team);
            squads.add(squad);
        }

        List<Integer> order = new ArrayList<>();
        for (int t = 0; t < TEAMS; t++) {
            order.add(t);
        }
        QuoteWeek week = FIRST;
        for (int w = 0; w < WEEKS; w++) {
            Collections.shuffle(order, random);
            for (int m = 0; m < TEAMS / 2; m++) {
                int home = order.get(2 * m);
                int away = order.get(2 * m + 1);
                Match match = database.finishedMatch("bench-" + w + "-" + m, week, teams.get(home), teams.get(away),
                        random.nextInt(4), random.nextInt(4), true);
                List<PlayerMatchStatsBuilder> lines = new ArrayList<>();
                Stream.concat(squads.get(home).stream(), squads.get(away).stream())
                        .forEach(player -> lines.add(randomLine(player, match)));
                database.stats(lines.toArray(PlayerMatchStatsBuilder[]::new));
            }
            week = week.next();
        }
        return squads.stream().flatMap(List::stream).toList();
    }

    private PlayerMatchStatsBuilder randomLine(Player player, Match match) {
        return stats().forPlayer(player).inMatch(match)
                .minutes(random.nextInt(91))
                .goals(random.nextInt(10) == 0 ? 1 : 0)
                .assists(random.nextInt(12) == 0 ? 1 : 0)
                .shots(random.nextInt(5))
                .shotsOnTarget(random.nextInt(3))
                .rating(String.format(Locale.ROOT, "%.1f", 5 + random.nextDouble() * 4));
    }

    private static long databaseSize() throws IOException {
        try (Stream<Path> files = Files.walk(DATABASE_DIRECTORY)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> path.toFile().length()).sum();
        }
    }

    private static QuoteWeek weekAfter(QuoteWeek week, int weeks) {
        QuoteWeek result = week;
        for (int i = 0; i < weeks; i++) {
            result = result.next();
        }
        return result;
    }

    private static <T> Timed<T> timed(Supplier<T> action) {
        long start = System.nanoTime();
        T value = action.get();
        return new Timed<>(value, Duration.ofNanos(System.nanoTime() - start));
    }

    private record Timed<T>(T value, Duration duration) {
    }
}
