package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyChangeService;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyService;

/**
 * La garantía estricta del caché con hilos reales (research R3 y R6 de la 013; FR-005 y SC-003):
 * una consulta en vuelo que leyó los datos viejos y guarda su resultado después del vaciado no deja
 * nada guardado, así que la consulta que empieza después de confirmado el cambio trae los datos
 * nuevos.
 *
 * <p>El esquema es el mismo para los tres cambios (recálculo, recotización y cambio de la
 * estrategia activa) y para la cotización actual: un repositorio espiado frena la consulta A
 * después de su última lectura (las cotizaciones, o el catálogo de estrategias cuando el cambio es
 * de estrategia, porque el ranking lo lee después de las cotizaciones), el hilo del test confirma el cambio con su vaciado de siempre, se
 * libera A y recién ahí empieza la consulta B. Solo se frena el hilo de A: el recálculo puede leer
 * la cotización actual en el hilo del test.</p>
 *
 * <p>Juegan Everton y Manchester United un partido por semana, desde la del 17/08/2026. El reloj
 * arranca el lunes 24/08 a la mañana, con la semana del 17/08 recién cerrada y cotizada con la
 * estrategia A.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-cache-concurrency-it;DB_CLOSE_DELAY=-1")
class QuoteCacheConcurrencyIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final Duration MONDAY_MORNING = Duration.ofHours(9);
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);
    private static final long TIMEOUT_SECONDS = 10;
    private static final String STRATEGY_A = "Concurrencia A";
    private static final String STRATEGY_B = "Concurrencia B";
    private static final String RANKING = "findRanking";
    private static final String CURRENT = "findCurrent";
    private static final String CATALOG = "findCatalog";

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(SECOND.start().plus(MONDAY_MORNING));
        }
    }

    @MockitoSpyBean
    private QuoteRepository quoteRepository;

    @Autowired
    private QuoteQueryService queryService;

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private QuoteCacheInvalidator cacheInvalidator;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyChangeService changeService;

    @MockitoSpyBean
    private StrategyRepository strategyRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationContext context;

    private final AtomicReference<Thread> frozenThread = new AtomicReference<>();
    private final AtomicReference<String> freezePoint = new AtomicReference<>();
    private final AtomicInteger rankingReads = new AtomicInteger();
    private final AtomicInteger currentReads = new AtomicInteger();
    private CountDownLatch entered;
    private CountDownLatch release;
    private ExecutorService executor;

    private QuoteDatabase database;
    private Team everton;
    private Team manUtd;
    private Player ndiaye;
    private Player zirkzee;
    private long strategyA;
    private long strategyB;

    @BeforeEach
    void prepare() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        executor = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> readThenMaybeFreeze(invocation, RANKING, rankingReads))
                .when(quoteRepository).findRanking(any(), anyInt(), anyInt());
        doAnswer(invocation -> readThenMaybeFreeze(invocation, CURRENT, currentReads))
                .when(quoteRepository).findCurrent(anyLong());
        doAnswer(invocation -> readThenMaybeFreeze(invocation, CATALOG, new AtomicInteger()))
                .when(strategyRepository).findCatalog();

        clock.set(SECOND.start().plus(MONDAY_MORNING));
        database = new QuoteDatabase(context);
        database.clean();
        strategyA = strategyNamed(STRATEGY_A, weights("0.50", "0.50"));
        strategyB = strategyNamed(STRATEGY_B, weights("0.25", "0.75"));
        changeService.activate(strategyA, RequoteTiming.NEXT_WEEK);
        cacheInvalidator.invalidateAll();
        everton = database.team("62", "Everton FC");
        manUtd = database.team("66", "Manchester United FC");
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        playWeek("560700", FIRST);
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        cacheInvalidator.invalidateAll();
    }

    @AfterEach
    void cleanUp() {
        release.countDown();
        executor.shutdownNow();
        database.clean();
        cacheInvalidator.invalidateAll();
    }

    @Test
    void unaConsultaDelRankingEnVueloDuranteUnRecalculoNoDejaGuardadaLaSemanaVieja() throws Exception {
        playWeek("560701", SECOND);
        clock.set(SECOND.end().plus(MONDAY_MORNING));
        Future<RankingPage> inFlight = rankingInFlight(RANKING);

        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        RankingPage stale = finish(inFlight);
        int readsBefore = rankingReads.get();
        RankingPage fresh = queryService.getRanking(NO_FILTER, 0, 10);

        assertThat(rankedWeeks(stale)).containsOnly(FIRST);
        assertThat(rankedWeeks(fresh)).hasSize(2).containsOnly(SECOND);
        assertThat(rankingReads.get()).isEqualTo(readsBefore + 1);
    }

    @Test
    void unaConsultaDelRankingEnVueloDuranteUnaRecotizacionNoDejaGuardadasLasCotizacionesViejas() throws Exception {
        Future<RankingPage> inFlight = rankingInFlight(RANKING);

        changeService.activate(strategyB, RequoteTiming.NOW);
        RankingPage stale = finish(inFlight);
        int readsBefore = rankingReads.get();
        RankingPage fresh = queryService.getRanking(NO_FILTER, 0, 10);

        assertThat(rankedTypes(stale)).containsOnly(QuoteType.CALCULATED);
        assertThat(rankedTypes(fresh)).hasSize(2).containsOnly(QuoteType.REQUOTED);
        assertThat(fresh.activeStrategy().strategyId()).isEqualTo(strategyB);
        assertThat(rankingReads.get()).isEqualTo(readsBefore + 1);
    }

    @Test
    void unaConsultaDelRankingEnVueloDuranteUnCambioDeEstrategiaNoDejaGuardadaLaEstrategiaVieja() throws Exception {
        Future<RankingPage> inFlight = rankingInFlight(CATALOG);

        changeService.activate(strategyB, RequoteTiming.NEXT_WEEK);
        RankingPage stale = finish(inFlight);
        int readsBefore = rankingReads.get();
        RankingPage fresh = queryService.getRanking(NO_FILTER, 0, 10);
        StrategyVersionRef active = strategyRepository.findCatalog().activeVersion().ref();

        assertThat(stale.activeStrategy().strategyId()).isEqualTo(strategyA);
        assertThat(fresh.activeStrategy()).isEqualTo(active);
        assertThat(fresh.activeStrategy().strategyId()).isEqualTo(strategyB);
        assertThat(rankingReads.get()).isEqualTo(readsBefore + 1);
    }

    @Test
    void unaConsultaDeLaCotizacionActualEnVueloDuranteUnRecalculoNoDejaGuardadaLaSemanaVieja() throws Exception {
        playWeek("560701", SECOND);
        clock.set(SECOND.end().plus(MONDAY_MORNING));
        freezePoint.set(CURRENT);
        Future<Quote> inFlight = executor.submit(() -> {
            frozenThread.set(Thread.currentThread());
            return queryService.getCurrent(ndiaye.id());
        });
        assertThat(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        Quote stale = finish(inFlight);
        int readsBefore = currentReads.get();
        Quote fresh = queryService.getCurrent(ndiaye.id());

        assertThat(stale.week()).isEqualTo(FIRST);
        assertThat(fresh.week()).isEqualTo(SECOND);
        assertThat(currentReads.get()).isEqualTo(readsBefore + 1);
    }

    // --- apoyo ---

    /**
     * Lee de verdad y, si es el hilo de la consulta en vuelo, avisa que entró y espera a que el test
     * lo libere. Solo se frena la primera lectura de ese hilo.
     */
    private Object readThenMaybeFreeze(InvocationOnMock invocation, String point, AtomicInteger reads)
            throws Throwable {
        reads.incrementAndGet();
        Object result = invocation.callRealMethod();
        if (point.equals(freezePoint.get()) && frozenThread.compareAndSet(Thread.currentThread(), null)) {
            entered.countDown();
            assertThat(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        return result;
    }

    private Future<RankingPage> rankingInFlight(String point) throws InterruptedException {
        freezePoint.set(point);
        Future<RankingPage> inFlight = executor.submit(() -> {
            frozenThread.set(Thread.currentThread());
            return queryService.getRanking(NO_FILTER, 0, 10);
        });
        assertThat(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return inFlight;
    }

    private <T> T finish(Future<T> inFlight) throws Exception {
        release.countDown();
        return inFlight.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Everton 1 - 0 Manchester United el sábado de esa semana, con las estadísticas de los dos.
     */
    private void playWeek(String externalId, QuoteWeek week) {
        Match match = database.finishedMatch(externalId, week, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1).tackles(2),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).tackles(1));
    }

    private long strategyNamed(String name, WeightSet defaultWeights) {
        Strategy existing = strategyRepository.findCatalog().findByName(name);
        if (existing != null) {
            return existing.id();
        }
        return strategyService.create(name, BigDecimal.TEN, new BigDecimal("10.00"), defaultWeights, Map.of())
                .strategy().id();
    }

    private static WeightSet weights(String goals, String tackles) {
        return new WeightSet(Map.of(ValuationMetric.GOALS, new BigDecimal(goals),
                ValuationMetric.TACKLES, new BigDecimal(tackles)));
    }

    private static List<QuoteWeek> rankedWeeks(RankingPage ranking) {
        return ranking.page().content().stream().map(RankedQuote::quote).map(Quote::week).toList();
    }

    private static List<QuoteType> rankedTypes(RankingPage ranking) {
        return ranking.page().content().stream().map(RankedQuote::quote).map(Quote::type).toList();
    }
}
