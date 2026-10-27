package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;

/**
 * La última completa exitosa de Football-Data que guarda {@link SyncService} (FR-047; HU3,
 * escenarios 7, 12 y 13) y la completa de la cadena, que corre con el candado ya tomado. Es una
 * clase aparte para no tocar {@code SyncServiceTest} más de lo avalado (research D26).
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceLastFullSyncTest {

    private static final String TOKEN = "token-de-prueba-de-la-ultima-completa";
    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(42);
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";

    @Mock
    private FootballDataAdapter adapter;

    @Mock
    private SyncWriteService writeService;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SyncReportLogger reportLogger;

    @Mock
    private LastFullSyncRepository lastFullSyncRepository;

    @Mock
    private Clock clock;

    private final SyncLock lock = new SyncLock();

    private SyncService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(STARTED_AT, FINISHED_AT);
        service = new SyncService(properties(), adapter, writeService, playerRepository, reportLogger,
                lastFullSyncRepository, lock, clock);
    }

    @Test
    void unaCompletaSemanalConLasCincoLigasEnExitoGuardaLaUltimaCompletaDeFootballData() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(lastFullSyncRepository).save(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
    }

    @Test
    void unaCompletaManualConLasCincoLigasEnExitoTambienLaGuarda() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.MANUAL);

        verify(lastFullSyncRepository).save(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
    }

    @Test
    void unaCompletaConUnaLigaFallidaNoGuardaNada() {
        when(adapter.fetchLeague(any())).thenAnswer(invocation -> {
            League league = invocation.getArgument(0);
            if (league == League.LIGUE_1) {
                throw new ExternalSourceException(TIMEOUT_REASON);
            }
            return snapshot(league);
        });
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(lastFullSyncRepository, never()).save(any());
    }

    @Test
    void unaDeUnaSolaLigaNoGuardaNadaAunqueSalgaBien() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();

        service.synchronizeLeague(League.SERIE_A);

        verify(lastFullSyncRepository, never()).save(any());
    }

    /**
     * La cadena toma el candado y después llama a {@code runFull}: no tiene que intentar tomarlo de
     * nuevo.
     */
    @Test
    void runFullCorreUnaCompletaConElCandadoYaTomadoPorQuienLoLlama() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();

        SyncReport report = lock.runExclusively(() -> service.runFull(SyncOrigin.WEEKLY));

        assertThat(report.type()).isEqualTo(SyncType.FULL);
        assertThat(report.origin()).isEqualTo(SyncOrigin.WEEKLY);
        assertThat(report.leagues()).hasSize(League.values().length).allMatch(LeagueSyncResult::succeeded);
        verify(lastFullSyncRepository).save(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
    }

    private void givenEverySourceLeague() {
        when(adapter.fetchLeague(any())).thenAnswer(invocation -> snapshot(invocation.<League>getArgument(0)));
    }

    private void givenEveryLeagueIsWritten() {
        when(writeService.applyLeague(any(), any())).thenAnswer(SyncServiceLastFullSyncTest::written);
    }

    /**
     * Lo que devolvería la escritura de la liga: un resultado exitoso armado por el modelo.
     */
    private static LeagueSyncResult written(InvocationOnMock invocation) {
        return new LeagueSync(invocation.<LeagueSnapshot>getArgument(0),
                invocation.<SquadAssignment>getArgument(1)).result();
    }

    private static FootballDataProperties properties() {
        return new FootballDataProperties(TOKEN, URI.create("http://football-data.invalid/v4"), Duration.ofSeconds(10),
                Duration.ofSeconds(30), new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", false));
    }
}
