package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StartupSyncTest {

    private static final String TOKEN = "token-de-prueba-del-arranque";
    private static final String MISSING_TOKEN_WARNING =
            "Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): la sincronización queda deshabilitada.";

    @Mock
    private SyncCoordinator coordinator;

    @Test
    void conTokenDelegaLaPuestaAlDiaEnElCoordinador() {
        StartupSync startupSync = startupSync(TOKEN, true);

        startupSync.catchUp();

        verify(coordinator).catchUpOnStartup();
    }

    @Test
    void sinTokenDejaLaAdvertenciaExactaYSePoneAlDiaIgual(CapturedOutput output) {
        StartupSync startupSync = startupSync(null, true);

        startupSync.catchUp();

        verify(coordinator).catchUpOnStartup();
        assertLine(output, MISSING_TOKEN_WARNING).contains("WARN");
    }

    @Test
    void conLaSincronizacionDeArranqueApagadaNoHaceNada(CapturedOutput output) {
        StartupSync startupSync = startupSync(TOKEN, false);

        startupSync.catchUp();

        verifyNoInteractions(coordinator);
        assertThat(output.getAll()).doesNotContain("El catálogo");
    }

    @Test
    void conOtraEnCursoDejaUnWarnSinPropagarLaExcepcion(CapturedOutput output) {
        doThrow(new SyncInProgressException()).when(coordinator).catchUpOnStartup();
        StartupSync startupSync = startupSync(TOKEN, true);

        assertThatCode(startupSync::catchUp).doesNotThrowAnyException();
        assertLine(output, "Se omite la sincronización de arranque")
                .contains("WARN")
                .contains("Ya hay una sincronización en curso.");
    }

    private StartupSync startupSync(String token, boolean onStartup) {
        FootballDataProperties properties = new FootballDataProperties(token,
                URI.create("http://football-data.invalid/v4"), Duration.ofSeconds(10), Duration.ofSeconds(30),
                new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", onStartup));
        return new StartupSync(properties, coordinator);
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
