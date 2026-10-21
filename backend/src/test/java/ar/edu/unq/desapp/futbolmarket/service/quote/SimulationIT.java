package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulatedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulationResult;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * La simulación contra la H2, con el reloj quieto el lunes 07/09/2026 a la mañana: las semanas
 * cerradas son la del 17/08, la del 24/08 y la del 31/08, como en {@code RecalculationIT}. Las
 * estrategias son las dos que siembra {@code StrategySeeder}, con la A activa.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:simulation-it;DB_CLOSE_DELAY=-1")
class SimulationIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final QuoteWeek THIRD = SECOND.next();
    private static final QuoteWeek CURRENT = THIRD.next();
    private static final Instant NOW = CURRENT.start().plus(Duration.ofHours(9));
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private QuoteSimulationService simulationService;

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private Team everton;
    private Team manUtd;
    private List<Player> active;

    @BeforeEach
    void prepareDatabase() {
        clock.set(NOW);
        database = new QuoteDatabase(context);
        database.clean();
        loadSeason();
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void simularNoCambiaCotizacionesEstrategiasNiLaActiva() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        List<Quote> quotesBefore = database.quotes();
        StrategyCatalog catalogBefore = strategyRepository.findCatalog();

        simulationService.simulate(inactiveStrategy().id(), null, NO_FILTER, 0, 10);
        simulationService.simulate(null, parametersOf(inactiveStrategy().latest()), NO_FILTER, 0, 10);

        StrategyCatalog catalogAfter = strategyRepository.findCatalog();
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(quotesBefore);
        assertThat(catalogAfter.strategies()).isEqualTo(catalogBefore.strategies());
        assertThat(catalogAfter.activeVersion()).isEqualTo(catalogBefore.activeVersion());
    }

    @Test
    void simularSinNadaCotizadoNoCreaNingunaCotizacion() {
        SimulationResult result = simulationService.simulate(activeStrategy().id(), null, NO_FILTER, 0, 10);

        assertThat(database.quoteCount()).isZero();
        assertThat(result.weeksSimulated()).isEqualTo(3);
        assertThat(result.page().content()).extracting(SimulatedQuote::currentValue).containsOnlyNulls();
    }

    @Test
    void dosSimulacionesIgualesDanResultadosIdenticos() {
        SimulationResult first = simulationService.simulate(inactiveStrategy().id(), null, NO_FILTER, 0, 10);

        SimulationResult second = simulationService.simulate(inactiveStrategy().id(), null, NO_FILTER, 0, 10);

        assertThat(second).isEqualTo(first);
        assertThat(first.page().content()).hasSize(active.size());
    }

    @Test
    void conElHistorialCotizadoConLaActivaLaSimuladaCoincideConLaActual() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        SimulationResult result = simulationService.simulate(activeStrategy().id(), null, NO_FILTER, 0, 10);

        assertThat(result.strategy()).isEqualTo(activeStrategy().latest().ref());
        assertThat(result.page().content()).hasSize(active.size()).allSatisfy(row -> {
            assertThat(row.currentValue()).isNotNull();
            assertThat(row.simulatedValue()).isEqualTo(row.currentValue());
            assertThat(row.difference()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void conOtraEstrategiaLaSimuladaSeComparaConLaActualGuardada() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        SimulationResult result = simulationService.simulate(inactiveStrategy().id(), null, NO_FILTER, 0, 10);

        assertThat(result.strategy()).isEqualTo(inactiveStrategy().latest().ref());
        assertThat(result.page().content()).hasSize(active.size()).allSatisfy(row -> {
            assertThat(row.currentValue()).isNotNull();
            assertThat(row.difference()).isEqualTo(row.simulatedValue().subtract(row.currentValue()));
        });
    }

    @Test
    void conParametrosSeCalculaSinGuardarlosNiInformarEstrategia() {
        int strategiesBefore = strategyRepository.findCatalog().strategies().size();
        var parameters = new QuoteSimulationService.Parameters(new BigDecimal("5"), new BigDecimal("10.00"),
                WeightSet.of(WeightSet.DEFAULT_LABEL, Map.of(
                        "GOALS", new BigDecimal("0.60"),
                        "SHOTS", new BigDecimal("0.20"),
                        "RATING", new BigDecimal("0.20"))), Map.of());

        SimulationResult result = simulationService.simulate(null, parameters, NO_FILTER, 0, 10);

        assertThat(result.strategy()).isNull();
        assertThat(result.weeksSimulated()).isEqualTo(3);
        assertThat(result.page().content()).hasSize(active.size());
        assertThat(result.page().content().getFirst().simulatedValue()).isGreaterThan(Quote.INITIAL_VALUE);
        assertThat(strategyRepository.findCatalog().strategies()).hasSize(strategiesBefore);
        assertThat(database.quoteCount()).isZero();
    }

    private Strategy activeStrategy() {
        return strategyRepository.findCatalog().activeStrategy();
    }

    private Strategy inactiveStrategy() {
        StrategyCatalog catalog = strategyRepository.findCatalog();
        return catalog.strategies().stream()
                .filter(strategy -> !catalog.isActive(strategy.id()))
                .findFirst()
                .orElseThrow();
    }

    private static QuoteSimulationService.Parameters parametersOf(StrategyVersion version) {
        return new QuoteSimulationService.Parameters(version.scaleFactor(), version.minimumValue(),
                version.defaultWeights(), version.positionWeights());
    }

    /**
     * Tres delanteros de Everton y Manchester United que juegan un partido por semana, un
     * mediocampista que no juega y un delantero inactivo.
     */
    private void loadSeason() {
        everton = database.team("62", "Everton FC");
        manUtd = database.team("66", "Manchester United FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player beto = database.player("8136", "Beto", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Player grealish = database.player("8140", "Jack Grealish", Position.MIDFIELDER, everton);
        database.inactive(database.player("8139", "Dominic Calvert-Lewin", Position.FORWARD, everton));
        active = List.of(ndiaye, beto, zirkzee, grealish);

        playWeek("560600", FIRST, 2, 1, ndiaye, 2, beto, 0, zirkzee, 1);
        playWeek("560601", SECOND, 0, 1, ndiaye, 0, beto, 0, zirkzee, 1);
        playWeek("560602", THIRD, 1, 1, ndiaye, 1, beto, 0, zirkzee, 1);
        database.scheduledMatch("560604", CURRENT, everton, manUtd);
    }

    private void playWeek(String externalId, QuoteWeek week, int evertonGoals, int manUtdGoals, Player ndiaye,
                          int ndiayeGoals, Player beto, int betoGoals, Player zirkzee, int zirkzeeGoals) {
        Match match = database.finishedMatch(externalId, week, everton, manUtd, evertonGoals, manUtdGoals, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(ndiayeGoals).shots(4).rating("7.5"),
                stats().forPlayer(beto).inMatch(match).minutes(70).goals(betoGoals).shots(1).rating("6.1"),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).goals(zirkzeeGoals).shots(2).rating("6.8"));
    }
}
