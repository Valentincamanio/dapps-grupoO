package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StartupSyncTest {

    private static final String TOKEN = "token-de-prueba-del-arranque";
    private static final String MISSING_TOKEN_WARNING =
            "Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): la sincronización queda deshabilitada.";

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SyncService syncService;

    @Test
    void conElCatalogoVacioYTokenDisparaUnaCompletaDeArranque(CapturedOutput output) {
        when(playerRepository.hasPlayers()).thenReturn(false);
        StartupSync startupSync = startupSync(TOKEN, true);

        startupSync.synchronizeIfCatalogIsEmpty();

        verify(syncService).synchronizeAll(SyncOrigin.STARTUP);
        assertLine(output, "El catálogo está vacío: se dispara una sincronización completa en segundo plano.")
                .contains("INFO");
    }

    @Test
    void conJugadoresNoDisparaYDejaElInfo(CapturedOutput output) {
        when(playerRepository.hasPlayers()).thenReturn(true);
        StartupSync startupSync = startupSync(TOKEN, true);

        startupSync.synchronizeIfCatalogIsEmpty();

        verifyNoInteractions(syncService);
        assertLine(output, "El catálogo ya tiene jugadores: no se sincroniza al arrancar.").contains("INFO");
    }

    @Test
    void sinTokenDejaLaAdvertenciaExactaYNoConsultaElCatalogo(CapturedOutput output) {
        StartupSync startupSync = startupSync(null, true);

        startupSync.synchronizeIfCatalogIsEmpty();

        verifyNoInteractions(playerRepository, syncService);
        assertLine(output, MISSING_TOKEN_WARNING).contains("WARN");
    }

    @Test
    void conLaSincronizacionDeArranqueApagadaNoHaceNada(CapturedOutput output) {
        StartupSync startupSync = startupSync(TOKEN, false);

        startupSync.synchronizeIfCatalogIsEmpty();

        verifyNoInteractions(playerRepository, syncService);
        assertThat(output.getAll()).doesNotContain("El catálogo");
    }

    @Test
    void conOtraEnCursoDejaUnWarnSinPropagarLaExcepcion(CapturedOutput output) {
        when(playerRepository.hasPlayers()).thenReturn(false);
        when(syncService.synchronizeAll(SyncOrigin.STARTUP)).thenThrow(new SyncInProgressException());
        StartupSync startupSync = startupSync(TOKEN, true);

        assertThatCode(startupSync::synchronizeIfCatalogIsEmpty).doesNotThrowAnyException();
        assertLine(output, "Se omite la sincronización de arranque")
                .contains("WARN")
                .contains("Ya hay una sincronización en curso.");
    }

    private StartupSync startupSync(String token, boolean onStartup) {
        FootballDataProperties properties = new FootballDataProperties(token,
                URI.create("http://football-data.invalid/v4"), Duration.ofSeconds(10), Duration.ofSeconds(30),
                new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", onStartup));
        return new StartupSync(properties, playerRepository, syncService);
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
