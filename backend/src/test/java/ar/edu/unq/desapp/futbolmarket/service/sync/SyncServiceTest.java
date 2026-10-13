package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedTeam;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.tsimikas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncDisabledException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private static final String TOKEN = "token-de-prueba-del-servicio";
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final String DISABLED_MESSAGE = "La sincronización con Football-Data.org está deshabilitada: "
            + "falta configurar la credencial de la fuente (FOOTBALL_DATA_TOKEN).";
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";
    private static final String FORBIDDEN_REASON =
            "La fuente rechazó la credencial o el recurso no está disponible en el plan contratado (403).";
    private static final String WRITE_FAILURE_REASON = "No se pudieron guardar los datos de la liga.";

    @Mock
    private FootballDataAdapter adapter;

    @Mock
    private SyncWriteService writeService;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SyncReportLogger reportLogger;

    private SyncService service;

    @BeforeEach
    void setUp() {
        service = service(TOKEN, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sinTokenUnaCompletaLanzaSyncDisabledExceptionSinConsultarLaFuente() {
        SyncService withoutToken = service(null, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> withoutToken.synchronizeAll(SyncOrigin.MANUAL))
                .isInstanceOf(SyncDisabledException.class)
                .hasMessage(DISABLED_MESSAGE);
        verifyNoInteractions(adapter, writeService, reportLogger);
    }

    @Test
    void sinTokenUnaDeUnaSolaLigaTambienLanzaSyncDisabledException() {
        SyncService withoutToken = service("  ", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> withoutToken.synchronizeLeague(League.PREMIER))
                .isInstanceOf(SyncDisabledException.class)
                .hasMessage(DISABLED_MESSAGE);
        verifyNoInteractions(adapter, writeService, reportLogger);
    }

    /**
     * El segundo pedido se hace desde la respuesta del adapter, es decir, con la primera
     * sincronización en curso. Así el test es determinístico y no necesita hilos.
     */
    @Test
    void unaSincronizacionPedidaMientrasCorreOtraLanzaSyncInProgressException() {
        AtomicReference<Throwable> nested = new AtomicReference<>();
        when(adapter.fetchLeague(any())).thenAnswer(invocation -> {
            nested.compareAndSet(null, catchThrowable(() -> service.synchronizeAll(SyncOrigin.WEEKLY)));
            return snapshot(invocation.<League>getArgument(0));
        });
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(nested.get())
                .isInstanceOf(SyncInProgressException.class)
                .hasMessage("Ya hay una sincronización en curso.");
    }

    @Test
    void alTerminarSePuedeVolverASincronizar() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.MANUAL);
        SyncReport second = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(second.leagues()).hasSize(League.values().length).allMatch(LeagueSyncResult::succeeded);
    }

    @Test
    void unaExcepcionInesperadaAlEscribirSePropagaYDespuesSePuedeVolverASincronizar() {
        givenEverySourceLeague();
        when(writeService.applyLeague(any(), any()))
                .thenThrow(new IllegalStateException("Error de programación."))
                .thenAnswer(SyncServiceTest::written);

        Throwable failure = catchThrowable(() -> service.synchronizeAll(SyncOrigin.MANUAL));
        SyncReport retry = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(retry.leagues()).hasSize(League.values().length).allMatch(LeagueSyncResult::succeeded);
    }

    @Test
    void unaCompletaDescargaTodoAntesDeEscribirLasCincoLigasEnOrdenInactivaUnaVezYRegistraElInforme() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();
        Player inactivated = savedPlayer(20L, tsimikas(), savedTeam(1L, liverpool(), League.PREMIER)).deactivate();
        when(writeService.deactivateMissing(any())).thenReturn(List.of(inactivated));

        SyncReport report = service.synchronizeAll(SyncOrigin.MANUAL);

        InOrder inOrder = inOrder(adapter, writeService);
        for (League league : League.values()) {
            inOrder.verify(adapter).fetchLeague(league);
        }
        for (League league : League.values()) {
            inOrder.verify(writeService).applyLeague(argThat(snapshot -> snapshot.league() == league), any());
        }
        inOrder.verify(writeService).deactivateMissing(any());
        verify(reportLogger).log(report);
        assertThat(report.type()).isEqualTo(SyncType.FULL);
        assertThat(report.origin()).isEqualTo(SyncOrigin.MANUAL);
        assertThat(report.inactivationApplied()).isTrue();
        assertThat(report.inactivatedPlayers()).containsExactly(inactivated);
    }

    @Test
    void unaLigaQueFallaAlDescargarNoSeEscribeLasDemasSiYNadieSeInactiva() {
        givenEverySourceLeague();
        doThrow(new ExternalSourceException(TIMEOUT_REASON)).when(adapter).fetchLeague(League.BUNDESLIGA);
        givenEveryLeagueIsWritten();

        SyncReport report = service.synchronizeAll(SyncOrigin.MANUAL);

        verify(writeService, times(4)).applyLeague(any(), any());
        verify(writeService, never()).applyLeague(argThat(snapshot -> snapshot.league() == League.BUNDESLIGA), any());
        verify(writeService, never()).deactivateMissing(any());
        assertThat(report.leagues())
                .extracting(LeagueSyncResult::league, LeagueSyncResult::status, LeagueSyncResult::failureReason)
                .containsExactly(
                        tuple(League.PREMIER, LeagueSyncStatus.SUCCEEDED, null),
                        tuple(League.BUNDESLIGA, LeagueSyncStatus.FAILED, TIMEOUT_REASON),
                        tuple(League.LA_LIGA, LeagueSyncStatus.SUCCEEDED, null),
                        tuple(League.SERIE_A, LeagueSyncStatus.SUCCEEDED, null),
                        tuple(League.LIGUE_1, LeagueSyncStatus.SUCCEEDED, null));
        assertThat(report.inactivationApplied()).isFalse();
    }

    @Test
    void unaFallaDeLaBaseAlEscribirUnaLigaLaDejaFallidaConSuMotivo() {
        givenEverySourceLeague();
        givenEveryLeagueIsWritten();
        doThrow(new DataIntegrityViolationException("ux_teams_external_id"))
                .when(writeService).applyLeague(argThat(snapshot -> snapshot.league() == League.LA_LIGA), any());

        SyncReport report = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(report.failedLeagues()).singleElement().satisfies(result -> {
            assertThat(result.league()).isEqualTo(League.LA_LIGA);
            assertThat(result.failureReason()).isEqualTo(WRITE_FAILURE_REASON);
        });
        verify(writeService, never()).deactivateMissing(any());
    }

    @Test
    void conLasCincoLigasFallidasDevuelveIgualElInformeSinLanzarNada() {
        when(adapter.fetchLeague(any())).thenThrow(new ExternalSourceException(FORBIDDEN_REASON));

        SyncReport report = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(report.leagues()).hasSize(League.values().length).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(LeagueSyncStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo(FORBIDDEN_REASON);
        });
        assertThat(report.inactivationApplied()).isFalse();
        verifyNoInteractions(writeService);
        verify(reportLogger).log(report);
    }

    @Test
    void unaDeUnaSolaLigaDescargaSoloEsaEsManualYNoInactiva() {
        when(adapter.fetchLeague(League.SERIE_A)).thenReturn(snapshot(League.SERIE_A));
        givenEveryLeagueIsWritten();

        SyncReport report = service.synchronizeLeague(League.SERIE_A);

        verify(adapter).fetchLeague(League.SERIE_A);
        verifyNoMoreInteractions(adapter);
        verify(writeService, never()).deactivateMissing(any());
        assertThat(report.type()).isEqualTo(SyncType.SINGLE_LEAGUE);
        assertThat(report.origin()).isEqualTo(SyncOrigin.MANUAL);
        assertThat(report.leagues()).extracting(LeagueSyncResult::league).containsExactly(League.SERIE_A);
        assertThat(report.inactivationApplied()).isFalse();
    }

    @Test
    void consultaLosJugadoresGuardadosPorLosIdsDuplicadosYLosUsaParaElegirSuEquipo() {
        givenEverySourceLeague();
        doReturn(snapshot(League.SERIE_A, team(JUVENTUS_ID, JUVENTUS_NAME, chiesa())))
                .when(adapter).fetchLeague(League.SERIE_A);
        Player chiesaInJuventus = savedPlayer(12L, chiesa(),
                savedTeam(5L, team(JUVENTUS_ID, JUVENTUS_NAME), League.SERIE_A));
        when(playerRepository.findAllByExternalIds(List.of(CHIESA_ID))).thenReturn(List.of(chiesaInJuventus));
        givenEveryLeagueIsWritten();

        service.synchronizeAll(SyncOrigin.MANUAL);

        ArgumentCaptor<SquadAssignment> assignment = ArgumentCaptor.forClass(SquadAssignment.class);
        verify(writeService, atLeastOnce()).applyLeague(any(), assignment.capture());
        assertThat(assignment.getValue().keeps(CHIESA_ID, JUVENTUS_ID)).isTrue();
        assertThat(assignment.getValue().keeps(CHIESA_ID, LIVERPOOL_ID)).isFalse();
    }

    @Test
    void elInicioYElFinDelInformeSalenDelReloj() {
        Instant startedAt = NOW;
        Instant finishedAt = NOW.plusSeconds(41);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startedAt, finishedAt);
        when(adapter.fetchLeague(any())).thenThrow(new ExternalSourceException(TIMEOUT_REASON));

        SyncReport report = service(TOKEN, clock).synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(report.startedAt()).isEqualTo(startedAt);
        assertThat(report.finishedAt()).isEqualTo(finishedAt);
    }

    private SyncService service(String token, Clock clock) {
        return new SyncService(properties(token), adapter, writeService, playerRepository, reportLogger, clock);
    }

    private void givenEverySourceLeague() {
        when(adapter.fetchLeague(any())).thenAnswer(invocation -> snapshot(invocation.<League>getArgument(0)));
    }

    private void givenEveryLeagueIsWritten() {
        when(writeService.applyLeague(any(), any())).thenAnswer(SyncServiceTest::written);
    }

    /**
     * Lo que devolvería la escritura de la liga: un resultado exitoso armado por el modelo.
     */
    private static LeagueSyncResult written(InvocationOnMock invocation) {
        return new LeagueSync(invocation.<LeagueSnapshot>getArgument(0),
                invocation.<SquadAssignment>getArgument(1)).result();
    }

    private static FootballDataProperties properties(String token) {
        return new FootballDataProperties(token, URI.create("http://football-data.invalid/v4"), Duration.ofSeconds(10),
                Duration.ofSeconds(30), new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", false));
    }
}
