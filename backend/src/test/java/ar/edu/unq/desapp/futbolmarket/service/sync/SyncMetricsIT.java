package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredPages;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncDisabledException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * El timer {@code futbolmarket.sync} alrededor de las sincronizaciones reales, con los dos adapters
 * mockeados como en {@code SyncChainIT} (US2.7 de la 012; research D6 y D12). Cada caso mide la
 * diferencia antes/después. También verifica que el proxy intercepta el {@code runFull}
 * package-private que llama la cadena, y que una completa manual cuenta una sola vez.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sync-metrics-it;DB_CLOSE_DELAY=-1",
        "futbolmarket.football-data.token=token-de-prueba-de-las-metricas"
})
class SyncMetricsIT {

    private static final String SYNC = "futbolmarket.sync";
    private static final String SOURCE = "source";
    private static final String TYPE = "type";
    private static final String RESULT = "result";
    private static final String FOOTBALL_DATA = "football_data";
    private static final String WHOSCORED = "whoscored";
    private static final String FULL = "full";
    private static final String SINGLE_LEAGUE = "single_league";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String IN_PROGRESS = "in_progress";
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";

    @Autowired
    private SyncService syncService;

    @Autowired
    private StatsSyncService statsSyncService;

    @Autowired
    private SyncCoordinator coordinator;

    @Autowired
    private SyncLock syncLock;

    @Autowired
    private MeterRegistry registry;

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
    void unaCompletaManualDeFootballDataSinFallasRegistraUnaSolaMuestraSuccess() {
        long success = count(FOOTBALL_DATA, FULL, SUCCESS);
        long all = countAll(FOOTBALL_DATA, FULL);

        syncService.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(count(FOOTBALL_DATA, FULL, SUCCESS)).isEqualTo(success + 1);
        assertThat(countAll(FOOTBALL_DATA, FULL)).isEqualTo(all + 1);
    }

    @Test
    void unaCompletaDeFootballDataConLigasFallidasEsFailure() {
        willThrow(new ExternalSourceException(TIMEOUT_REASON)).given(footballDataAdapter).fetchLeague(any());
        long before = count(FOOTBALL_DATA, FULL, FAILURE);

        syncService.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(count(FOOTBALL_DATA, FULL, FAILURE)).isEqualTo(before + 1);
    }

    @Test
    void unaLigaDeFootballDataSinFallasEsSuccessYConFallaEsFailure() {
        long success = count(FOOTBALL_DATA, SINGLE_LEAGUE, SUCCESS);
        long failure = count(FOOTBALL_DATA, SINGLE_LEAGUE, FAILURE);

        syncService.synchronizeLeague(League.PREMIER);
        willThrow(new ExternalSourceException(TIMEOUT_REASON)).given(footballDataAdapter).fetchLeague(any());
        syncService.synchronizeLeague(League.PREMIER);

        assertThat(count(FOOTBALL_DATA, SINGLE_LEAGUE, SUCCESS)).isEqualTo(success + 1);
        assertThat(count(FOOTBALL_DATA, SINGLE_LEAGUE, FAILURE)).isEqualTo(failure + 1);
    }

    @Test
    void unaCompletaManualDeWhoScoredSinCorteRegistraUnaSolaMuestraSuccess() {
        long success = count(WHOSCORED, FULL, SUCCESS);
        long all = countAll(WHOSCORED, FULL);

        statsSyncService.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(count(WHOSCORED, FULL, SUCCESS)).isEqualTo(success + 1);
        assertThat(countAll(WHOSCORED, FULL)).isEqualTo(all + 1);
    }

    @Test
    void unaCompletaDeWhoScoredConCorteEsFailure() {
        loadCatalog();
        willThrow(new SourceBlockedException(WhoScoredPages.league(League.PREMIER)))
                .given(whoScoredAdapter).fetchLeagueTeams(any());
        long before = count(WHOSCORED, FULL, FAILURE);

        statsSyncService.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(count(WHOSCORED, FULL, FAILURE)).isEqualTo(before + 1);
    }

    @Test
    void unaLigaDeWhoScoredSinCorteEsSuccessYConCorteEsFailure() {
        long success = count(WHOSCORED, SINGLE_LEAGUE, SUCCESS);
        long failure = count(WHOSCORED, SINGLE_LEAGUE, FAILURE);

        statsSyncService.synchronizeLeague(League.PREMIER);
        loadCatalog();
        willThrow(new SourceBlockedException(WhoScoredPages.league(League.PREMIER)))
                .given(whoScoredAdapter).fetchLeagueTeams(any());
        statsSyncService.synchronizeLeague(League.PREMIER);

        assertThat(count(WHOSCORED, SINGLE_LEAGUE, SUCCESS)).isEqualTo(success + 1);
        assertThat(count(WHOSCORED, SINGLE_LEAGUE, FAILURE)).isEqualTo(failure + 1);
    }

    @Test
    void conElCandadoTomadoLosPedidosDeCadaFuenteSonInProgress() {
        long footballData = count(FOOTBALL_DATA, FULL, IN_PROGRESS);
        long whoScored = count(WHOSCORED, SINGLE_LEAGUE, IN_PROGRESS);

        Throwable first = catchThrowable(() -> syncLock.runExclusively(
                () -> syncService.synchronizeAll(SyncOrigin.MANUAL)));
        Throwable second = catchThrowable(() -> syncLock.runExclusively(
                () -> statsSyncService.synchronizeLeague(League.PREMIER)));

        assertThat(first).isInstanceOf(SyncInProgressException.class);
        assertThat(second).isInstanceOf(SyncInProgressException.class);
        assertThat(count(FOOTBALL_DATA, FULL, IN_PROGRESS)).isEqualTo(footballData + 1);
        assertThat(count(WHOSCORED, SINGLE_LEAGUE, IN_PROGRESS)).isEqualTo(whoScored + 1);
    }

    @Test
    void laCadenaConTokenRegistraUnaMuestraCompletaDeCadaFuente() {
        long footballData = countAll(FOOTBALL_DATA, FULL);
        long whoScored = countAll(WHOSCORED, FULL);

        coordinator.runWeeklyChain();

        assertThat(countAll(FOOTBALL_DATA, FULL)).isEqualTo(footballData + 1);
        assertThat(countAll(WHOSCORED, FULL)).isEqualTo(whoScored + 1);
    }

    /**
     * Sin token de Football-Data, en un contexto propio con otra base.
     */
    @Nested
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:h2:mem:sync-metrics-without-token-it;DB_CLOSE_DELAY=-1",
            "futbolmarket.football-data.token="
    })
    class WithoutToken {

        @Test
        void unPedidoDeFootballDataSinTokenNoRegistraNada() {
            long footballData = countAll(FOOTBALL_DATA, FULL) + countAll(FOOTBALL_DATA, SINGLE_LEAGUE);

            Throwable all = catchThrowable(() -> syncService.synchronizeAll(SyncOrigin.MANUAL));
            Throwable league = catchThrowable(() -> syncService.synchronizeLeague(League.PREMIER));

            assertThat(all).isInstanceOf(SyncDisabledException.class);
            assertThat(league).isInstanceOf(SyncDisabledException.class);
            assertThat(countAll(FOOTBALL_DATA, FULL) + countAll(FOOTBALL_DATA, SINGLE_LEAGUE)).isEqualTo(footballData);
        }

        @Test
        void laCadenaSinTokenRegistraUnaSolaMuestraDeWhoScoredYNingunaDeFootballData() {
            long footballData = countAll(FOOTBALL_DATA, FULL);
            long whoScored = countAll(WHOSCORED, FULL);

            coordinator.runWeeklyChain();

            assertThat(countAll(FOOTBALL_DATA, FULL)).isEqualTo(footballData);
            assertThat(countAll(WHOSCORED, FULL)).isEqualTo(whoScored + 1);
        }
    }

    /**
     * Sin equipos ni partidos WhoScored no pide ninguna página y no puede haber corte: primero se
     * carga el catálogo desde Football-Data, como en la cadena.
     */
    private void loadCatalog() {
        syncService.synchronizeAll(SyncOrigin.MANUAL);
    }

    private long count(String source, String type, String result) {
        return registry.get(SYNC).tag(SOURCE, source).tag(TYPE, type).tag(RESULT, result).timer().count();
    }

    private long countAll(String source, String type) {
        return registry.get(SYNC).tag(SOURCE, source).tag(TYPE, type).timers().stream()
                .mapToLong(Timer::count).sum();
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
