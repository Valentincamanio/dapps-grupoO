package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.config.SyncProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncRun;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;

/**
 * La cadena y la puesta al día al arrancar (research D22; HU3, escenarios 1 a 5, 7, 8 y 10). El
 * candado es el real; los dos servicios y el repository están mockeados, y el reloj está fijo.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SyncCoordinatorTest {

    private static final String TOKEN = "token-de-prueba-del-coordinador";
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final Duration CATCH_UP_AFTER = Duration.ofDays(7);
    private static final Instant EIGHT_DAYS_AGO = NOW.minus(Duration.ofDays(8));
    private static final Instant NINE_DAYS_AGO = NOW.minus(Duration.ofDays(9));
    private static final Instant THREE_DAYS_AGO = NOW.minus(Duration.ofDays(3));
    private static final Instant TWO_DAYS_AGO = NOW.minus(Duration.ofDays(2));
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";

    @Mock
    private SyncService syncService;

    @Mock
    private StatsSyncService statsSyncService;

    @Mock
    private LastFullSyncRepository lastFullSyncRepository;

    private final SyncLock lock = new SyncLock();

    @Test
    void laCadenaSemanalCorreFootballDataYDespuesWhoScored() {
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.runWeeklyChain();

        InOrder order = inOrder(syncService, statsSyncService);
        order.verify(syncService).runFull(SyncOrigin.WEEKLY);
        order.verify(statsSyncService).runFull(SyncOrigin.WEEKLY);
        verifyNoMoreInteractions(syncService, statsSyncService);
    }

    @Test
    void sinTokenLaCadenaSalteaFootballDataConUnInfoYCorreWhoScored(CapturedOutput output) {
        SyncCoordinator coordinator = coordinator(null, CATCH_UP_AFTER);

        coordinator.runWeeklyChain();

        verifyNoInteractions(syncService);
        verify(statsSyncService).runFull(SyncOrigin.WEEKLY);
        assertLine(output, "Se omite Football-Data.org en la cadena: falta la credencial (FOOTBALL_DATA_TOKEN).")
                .contains("INFO");
    }

    @Test
    void conFootballDataFallidaEnLasCincoLigasWhoScoredCorreIgual() {
        when(syncService.runFull(SyncOrigin.WEEKLY)).thenReturn(failedInEveryLeague());
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.runWeeklyChain();

        verify(statsSyncService).runFull(SyncOrigin.WEEKLY);
    }

    @Test
    void unErrorDeProgramacionEnFootballDataAbortaLaCadenaYLiberaElCandado() {
        when(syncService.runFull(SyncOrigin.WEEKLY)).thenThrow(new IllegalStateException("falla inesperada"));
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        assertThatThrownBy(coordinator::runWeeklyChain).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(statsSyncService);
        assertThat(lock.runExclusively(() -> "siguiente")).isEqualTo("siguiente");
    }

    /**
     * Cada disparo manual toma el mismo candado que la cadena. Con los servicios mockeados, el
     * disparo se simula pidiendo el candado desde adentro de cada paso.
     */
    @Test
    void unDisparoManualPedidoDesdeAdentroDeCadaPasoLanzaSyncInProgressException() {
        AtomicReference<Throwable> duringFootballData = new AtomicReference<>();
        AtomicReference<Throwable> duringWhoScored = new AtomicReference<>();
        when(syncService.runFull(SyncOrigin.WEEKLY)).thenAnswer(invocation -> {
            duringFootballData.set(catchThrowable(() -> lock.runExclusively(() -> "manual de Football-Data")));
            return null;
        });
        when(statsSyncService.runFull(SyncOrigin.WEEKLY)).thenAnswer(invocation -> {
            duringWhoScored.set(catchThrowable(() -> lock.runExclusively(() -> "manual de WhoScored")));
            return null;
        });
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.runWeeklyChain();

        assertThat(List.of(duringFootballData.get(), duringWhoScored.get())).allSatisfy(thrown -> assertThat(thrown)
                .isInstanceOf(SyncInProgressException.class)
                .hasMessage("Ya hay una sincronización en curso."));
    }

    @Test
    void conElCandadoTomadoLaCadenaSemanalLanzaSyncInProgressException() {
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        Throwable thrown = lock.runExclusively(() -> catchThrowable(coordinator::runWeeklyChain));

        assertThat(thrown).isInstanceOf(SyncInProgressException.class);
        verifyNoInteractions(syncService, statsSyncService);
    }

    @Test
    void conElCandadoTomadoLaPuestaAlDiaLanzaSyncInProgressException() {
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        Throwable thrown = lock.runExclusively(() -> catchThrowable(coordinator::catchUpOnStartup));

        assertThat(thrown).isInstanceOf(SyncInProgressException.class);
        verifyNoInteractions(syncService, statsSyncService);
    }

    @Test
    void sinNingunaCompletaDeFootballDataLaPuestaAlDiaCorreLaCadena(CapturedOutput output) {
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        InOrder order = inOrder(syncService, statsSyncService);
        order.verify(syncService).runFull(SyncOrigin.CATCH_UP);
        order.verify(statsSyncService).runFull(SyncOrigin.CATCH_UP);
        assertLine(output, "Puesta al día al arrancar").contains("INFO").endsWith("Puesta al día al arrancar: no hay "
                + "una sincronización completa exitosa de Football-Data.org registrada: se corren las dos "
                + "sincronizaciones en segundo plano.");
    }

    @Test
    void conLaDeFootballDataVencidaLaPuestaAlDiaCorreLaCadenaYDiceCuandoTermino(CapturedOutput output) {
        givenLastFullSyncs(EIGHT_DAYS_AGO, TWO_DAYS_AGO);
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        InOrder order = inOrder(syncService, statsSyncService);
        order.verify(syncService).runFull(SyncOrigin.CATCH_UP);
        order.verify(statsSyncService).runFull(SyncOrigin.CATCH_UP);
        assertLine(output, "Puesta al día al arrancar").contains("INFO").endsWith("Puesta al día al arrancar: la "
                + "última sincronización completa exitosa de Football-Data.org terminó el 2026-10-04T07:00:00Z: se "
                + "corren las dos sincronizaciones en segundo plano.");
    }

    @Test
    void conFootballDataAlDiaYSinNingunaDeWhoScoredCorreSoloWhoScored(CapturedOutput output) {
        givenLastFullSyncs(THREE_DAYS_AGO, null);
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        verify(statsSyncService).synchronizeAll(SyncOrigin.CATCH_UP);
        verifyNoMoreInteractions(statsSyncService);
        verifyNoInteractions(syncService);
        assertLine(output, "Puesta al día al arrancar").contains("INFO").endsWith("Puesta al día al arrancar: no hay "
                + "una sincronización completa exitosa de WhoScored registrada: se corre solo la de WhoScored en "
                + "segundo plano.");
    }

    @Test
    void conLaDeWhoScoredVencidaCorreSoloWhoScoredYDiceCuandoTermino(CapturedOutput output) {
        givenLastFullSyncs(THREE_DAYS_AGO, NINE_DAYS_AGO);
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        verify(statsSyncService).synchronizeAll(SyncOrigin.CATCH_UP);
        verifyNoInteractions(syncService);
        assertLine(output, "Puesta al día al arrancar").contains("INFO").endsWith("Puesta al día al arrancar: la "
                + "última sincronización completa exitosa de WhoScored terminó el 2026-10-03T07:00:00Z: se corre "
                + "solo la de WhoScored en segundo plano.");
    }

    @Test
    void conLasDosAlDiaNoSincronizaNada(CapturedOutput output) {
        givenLastFullSyncs(THREE_DAYS_AGO, TWO_DAYS_AGO);
        SyncCoordinator coordinator = coordinator(TOKEN, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        verifyNoInteractions(syncService, statsSyncService);
        assertLine(output, "Las dos fuentes están al día: no se sincroniza al arrancar.").contains("INFO");
    }

    @Test
    void laAntiguedadMaximaSaleDeLaConfiguracion() {
        givenLastFullSyncs(THREE_DAYS_AGO, TWO_DAYS_AGO);
        SyncCoordinator coordinator = coordinator(TOKEN, Duration.ofDays(1));

        coordinator.catchUpOnStartup();

        verify(syncService).runFull(SyncOrigin.CATCH_UP);
        verify(statsSyncService).runFull(SyncOrigin.CATCH_UP);
    }

    @Test
    void sinTokenLaPuestaAlDiaSalteaFootballDataYCorreWhoScored(CapturedOutput output) {
        SyncCoordinator coordinator = coordinator(null, CATCH_UP_AFTER);

        coordinator.catchUpOnStartup();

        verifyNoInteractions(syncService);
        verify(statsSyncService).runFull(SyncOrigin.CATCH_UP);
        assertLine(output, "Se omite Football-Data.org en la cadena").contains("INFO");
    }

    /**
     * Una celda en {@code null} es una fuente sin ninguna completa registrada.
     */
    private void givenLastFullSyncs(Instant footballData, Instant whoScored) {
        when(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA))
                .thenReturn(Optional.ofNullable(footballData)
                        .map(at -> new LastFullSync(SyncSource.FOOTBALL_DATA, at)));
        when(lastFullSyncRepository.find(SyncSource.WHOSCORED))
                .thenReturn(Optional.ofNullable(whoScored).map(at -> new LastFullSync(SyncSource.WHOSCORED, at)));
    }

    private static SyncReport failedInEveryLeague() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, NOW);
        for (League league : League.values()) {
            run.recordFailure(league, TIMEOUT_REASON);
        }
        return run.finish(NOW, List.of());
    }

    private SyncCoordinator coordinator(String token, Duration catchUpAfter) {
        FootballDataProperties footballData = new FootballDataProperties(token,
                URI.create("http://football-data.invalid/v4"), Duration.ofSeconds(10), Duration.ofSeconds(30),
                new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", false));
        return new SyncCoordinator(footballData, new SyncProperties(catchUpAfter), lock, syncService,
                statsSyncService, lastFullSyncRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
