package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredPages;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;

/**
 * La cadena con los dos adapters mockeados (research D22; HU3, escenarios 6, 7, 10, 12 y 13). El
 * adapter de Football-Data devuelve las ligas de {@code SnapshotFixtures}; el de WhoScored no
 * devuelve ningún equipo, así que los partidos terminados quedan omitidos sin cruzar y la corrida
 * termina sin corte.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1",
        "futbolmarket.football-data.token=token-de-prueba-de-la-cadena"
})
class SyncChainIT {

    private static final Instant PREVIOUS_FINISH = Instant.parse("2026-10-05T07:12:45Z");
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";
    private static final long LATCH_TIMEOUT_SECONDS = 10;

    @Autowired
    private SyncCoordinator coordinator;

    @Autowired
    private SyncService syncService;

    @Autowired
    private StatsSyncService statsSyncService;

    @Autowired
    private LastFullSyncRepository lastFullSyncRepository;

    @Autowired
    private MatchRepository matchRepository;

    @MockitoBean
    private FootballDataAdapter footballDataAdapter;

    @MockitoBean
    private WhoScoredAdapter whoScoredAdapter;

    @Autowired
    private PlayerMatchStatsSQLDAO statsDAO;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @Autowired
    private LastFullSyncSQLDAO lastFullSyncDAO;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        given(footballDataAdapter.fetchLeague(any()))
                .willAnswer(invocation -> snapshot(invocation.<League>getArgument(0)));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void laCadenaSemanalCorreFootballDataYDespuesWhoScoredYGuardaLaUltimaCompletaDeLasDos() {
        coordinator.runWeeklyChain();

        InOrder order = inOrder(footballDataAdapter, whoScoredAdapter);
        order.verify(footballDataAdapter).fetchLeague(League.LIGUE_1);
        order.verify(whoScoredAdapter).fetchLeagueTeams(League.PREMIER);
        assertThat(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA)).isPresent();
        assertThat(lastFullSyncRepository.find(SyncSource.WHOSCORED)).isPresent();
    }

    @Test
    void conFootballDataFallandoEnLasCincoLigasWhoScoredCorreIgualYLaUltimaDeFootballDataNoCambia() {
        LastFullSync previous =
                lastFullSyncRepository.save(new LastFullSync(SyncSource.FOOTBALL_DATA, PREVIOUS_FINISH));
        willThrow(new ExternalSourceException(TIMEOUT_REASON)).given(footballDataAdapter).fetchLeague(any());

        coordinator.runWeeklyChain();

        assertThat(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA)).contains(previous);
        assertThat(lastFullSyncRepository.find(SyncSource.WHOSCORED)).isPresent();
    }

    /**
     * La cadena queda detenida primero en el adapter de Football-Data y después en el de WhoScored,
     * ya entre los dos pasos. En las dos paradas, los dos disparos manuales se rechazan. Todas las
     * esperas tienen timeout, para que un error no deje el test colgado.
     */
    @Test
    void conLaCadenaEnCursoLosDosDisparosManualesLanzanSyncInProgressException() throws Exception {
        CountDownLatch footballDataEntered = new CountDownLatch(1);
        CountDownLatch releaseFootballData = new CountDownLatch(1);
        CountDownLatch whoScoredEntered = new CountDownLatch(1);
        CountDownLatch releaseWhoScored = new CountDownLatch(1);
        willAnswer(invocation -> {
            footballDataEntered.countDown();
            await(releaseFootballData);
            return snapshot(invocation.<League>getArgument(0));
        }).given(footballDataAdapter).fetchLeague(any());
        willAnswer(invocation -> {
            whoScoredEntered.countDown();
            await(releaseWhoScored);
            return List.of();
        }).given(whoScoredAdapter).fetchLeagueTeams(any());

        CompletableFuture<Void> chain = CompletableFuture.runAsync(coordinator::runWeeklyChain);
        boolean stoppedInFootballData = footballDataEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<Throwable> duringFootballData = manualTriggers();
        releaseFootballData.countDown();
        boolean stoppedInWhoScored = whoScoredEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<Throwable> duringWhoScored = manualTriggers();
        releaseWhoScored.countDown();
        chain.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(stoppedInFootballData).isTrue();
        assertThat(stoppedInWhoScored).isTrue();
        assertThat(duringFootballData).hasSize(2).allSatisfy(thrown -> assertThat(thrown)
                .isInstanceOf(SyncInProgressException.class)
                .hasMessage("Ya hay una sincronización en curso."));
        assertThat(duringWhoScored).hasSize(2)
                .allSatisfy(thrown -> assertThat(thrown).isInstanceOf(SyncInProgressException.class));
    }

    @Test
    void unaCompletaManualDeFootballDataConLasCincoLigasEnExitoGuardaSuUltimaCompleta() {
        syncService.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA)).isPresent();
    }

    @Test
    void unaDeUnaSolaLigaNoCambiaLaUltimaCompletaDeFootballData() {
        LastFullSync previous =
                lastFullSyncRepository.save(new LastFullSync(SyncSource.FOOTBALL_DATA, PREVIOUS_FINISH));

        syncService.synchronizeLeague(League.PREMIER);

        Optional<LastFullSync> after = lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA);
        assertThat(after).contains(previous);
    }

    /**
     * WhoScored rechaza la página de la Premier, que es el primer pedido de la corrida (HU4,
     * escenario 9).
     */
    @Test
    void conWhoScoredBloqueandoDesdeElPrimerPedidoLaCadenaTerminaYSoloGuardaLaUltimaDeFootballData() {
        willThrow(new SourceBlockedException(WhoScoredPages.league(League.PREMIER)))
                .given(whoScoredAdapter).fetchLeagueTeams(any());

        Throwable thrown = catchThrowable(coordinator::runWeeklyChain);

        assertThat(thrown).isNull();
        assertThat(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA)).isPresent();
        assertThat(lastFullSyncRepository.find(SyncSource.WHOSCORED)).isEmpty();
        assertThat(matchRepository.findAllByExternalIds(
                Arrays.stream(League.values()).map(SnapshotFixtures::finishedMatchId).toList()))
                .hasSize(League.values().length)
                .allSatisfy(match -> assertThat(match.needsStats()).isTrue());
    }

    /**
     * El disparo manual de cada fuente, como lo pide su controller.
     */
    private List<Throwable> manualTriggers() {
        return List.of(
                catchThrowable(() -> syncService.synchronizeAll(SyncOrigin.MANUAL)),
                catchThrowable(() -> statsSyncService.synchronizeAll(SyncOrigin.MANUAL)));
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("El test no liberó la cadena a tiempo.");
        }
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        lastFullSyncDAO.deleteAll();
    }
}
