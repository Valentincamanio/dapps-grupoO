package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyChangeConflictException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteRecalculationService;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationLock;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;

/**
 * Cambiar la estrategia activa con un recálculo o una sincronización en curso, contra la H2 y sin
 * hilos ni esperas (FR-038, FR-039).
 *
 * <p>Los candados se toman a propósito corriendo el cambio adentro de su {@code runExclusively}:
 * como no son reentrantes, el cambio los encuentra ocupados. Para cambiar la activa en medio de un
 * recálculo, el reloj del test corre el cambio la primera vez que el recálculo lo lee, justo
 * después de leer la estrategia activa.</p>
 *
 * <p>Las estrategias son las dos de arranque. Las semanas del 17/08 y del 24/08 de 2026 tienen un
 * Everton - Manchester United cada una; el reloj está en la semana del 31/08.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:strategy-change-it;DB_CLOSE_DELAY=-1")
class StrategyChangeIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final Instant DURING_THIRD = SECOND.end().plus(Duration.ofDays(2)).plus(Duration.ofHours(15));

    /**
     * Un reloj quieto que, si se le pide, corre una acción la próxima vez que alguien lo lee.
     */
    static final class HookedClock extends Clock {
        private volatile Instant now;
        private volatile Runnable onNextRead;

        HookedClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void onNextRead(Runnable action) {
            onNextRead = action;
        }

        @Override
        public Instant instant() {
            Runnable action = onNextRead;
            if (action != null) {
                onNextRead = null;
                action.run();
            }
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }
    }

    @TestConfiguration
    static class HookedClockConfiguration {

        @Bean
        @Primary
        HookedClock testClock() {
            return new HookedClock(DURING_THIRD);
        }
    }

    @Autowired
    private StrategyChangeService changeService;

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private RecalculationLock recalculationLock;

    @Autowired
    private SyncLock syncLock;

    @Autowired
    private HookedClock clock;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private long strategyA;
    private long strategyB;

    @BeforeEach
    void prepareDatabase() {
        clock.set(DURING_THIRD);
        clock.onNextRead(null);
        database = new QuoteDatabase(context);
        database.clean();
        StrategyCatalog catalog = strategyRepository.findCatalog();
        strategyA = catalog.findByName(StartingStrategies.MATCH_PERFORMANCE.name()).id();
        strategyB = catalog.findByName(StartingStrategies.BALANCED_BY_POSITION.name()).id();
        changeService.activate(strategyA, RequoteTiming.NEXT_WEEK);
        loadSeason();
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void conUnRecalculoEnCursoAhoraRespondeConflictoYNiLaActivaNiLasCotizacionesCambian() {
        List<Quote> before = database.quotes();

        recalculationLock.runExclusively(() -> {
            assertThatThrownBy(() -> changeService.activate(strategyB, RequoteTiming.NOW))
                    .isInstanceOf(RecalculationInProgressException.class)
                    .hasMessage("Ya hay un recálculo en curso.");
            return null;
        });

        assertThat(activeVersion().strategyId()).isEqualTo(strategyA);
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void conUnaSincronizacionEnCursoAhoraRespondeConflictoYNiLaActivaNiLasCotizacionesCambian() {
        List<Quote> before = database.quotes();
        StrategyVersion activeBefore = activeVersion();

        syncLock.runExclusively(() -> {
            assertThatThrownBy(() -> changeService.activate(strategyB, RequoteTiming.NOW))
                    .isInstanceOf(RecalculationBlockedBySyncException.class)
                    .hasMessage("No se puede recalcular: hay una sincronización en curso.");
            assertThatThrownBy(() -> changeService.createVersion(strategyA, null, null,
                    activeBefore.defaultWeights(), Map.of(), RequoteTiming.NOW))
                    .isInstanceOf(RecalculationBlockedBySyncException.class);
            return null;
        });

        assertThat(activeVersion()).isEqualTo(activeBefore);
        assertThat(strategyRepository.findCatalog().find(strategyA).versions()).hasSize(1);
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void laSemanaSiguienteSeAplicaAunqueHayaUnRecalculoEnCurso() {
        long before = database.quoteCount();

        StrategyChangeResult result = recalculationLock.runExclusively(
                () -> changeService.activate(strategyB, RequoteTiming.NEXT_WEEK));

        assertThat(result.requotation()).isEmpty();
        assertThat(activeVersion().strategyId()).isEqualTo(strategyB);
        assertThat(database.quoteCount()).isEqualTo(before);
    }

    @Test
    void unRecalculoEnCursoTerminaConLaEstrategiaQueTeniaAlEmpezar() {
        database.clean();
        loadSeason();
        StrategyVersion atStart = activeVersion();
        clock.onNextRead(() -> changeService.activate(strategyB, RequoteTiming.NEXT_WEEK));

        RecalculationReport report = recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(activeVersion().strategyId()).isEqualTo(strategyB);
        assertThat(report.strategy()).isEqualTo(atStart.ref());
        assertThat(report.quotedWeeks()).isNotEmpty();
        assertThat(database.quotes()).isNotEmpty()
                .allSatisfy(quote -> assertThat(quote.strategy()).isEqualTo(atStart.ref()));
    }

    @Test
    void unaFallaAlRecotizarDejaLaActivaIntactaYNoGuardaNada() {
        List<Quote> before = database.quotes();
        clock.set(SECOND.end());

        assertThatThrownBy(() -> changeService.activate(strategyB, RequoteTiming.NOW))
                .isInstanceOf(StrategyChangeConflictException.class)
                .hasMessage("La estrategia cambió mientras se guardaba: reintente.");

        assertThat(activeVersion().strategyId()).isEqualTo(strategyA);
        assertThat(database.quotes()).containsExactlyInAnyOrderElementsOf(before)
                .noneMatch(quote -> quote.type() == QuoteType.REQUOTED);
    }

    @Test
    void trasUnConflictoElCandadoQuedaLibre() {
        clock.set(SECOND.end());
        assertThatThrownBy(() -> changeService.activate(strategyB, RequoteTiming.NOW))
                .isInstanceOf(StrategyChangeConflictException.class);
        clock.set(DURING_THIRD);

        StrategyChangeResult result = changeService.activate(strategyB, RequoteTiming.NOW);

        assertThat(result.requotation()).isPresent();
        assertThat(activeVersion().strategyId()).isEqualTo(strategyB);
    }

    private StrategyVersion activeVersion() {
        return strategyRepository.findCatalog().activeVersion();
    }

    /**
     * Tres delanteros de Everton y Manchester United, con un partido por semana.
     */
    private void loadSeason() {
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player beto = database.player("8136", "Beto", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Match first = database.finishedMatch("560600", FIRST, everton, manUtd, 2, 1, true);
        Match second = database.finishedMatch("560601", SECOND, everton, manUtd, 0, 1, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(first).minutes(90).goals(2).rating("7.5"),
                stats().forPlayer(beto).inMatch(first).minutes(70).goals(0).rating("6.1"),
                stats().forPlayer(zirkzee).inMatch(first).minutes(90).goals(1).rating("6.8"),
                stats().forPlayer(ndiaye).inMatch(second).minutes(90).goals(0).rating("6.0"),
                stats().forPlayer(beto).inMatch(second).minutes(90).goals(0).rating("6.4"),
                stats().forPlayer(zirkzee).inMatch(second).minutes(90).goals(1).rating("7.9"));
    }
}
