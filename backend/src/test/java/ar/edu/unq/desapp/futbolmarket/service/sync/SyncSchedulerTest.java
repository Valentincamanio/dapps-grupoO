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
import org.springframework.scheduling.annotation.Scheduled;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SyncSchedulerTest {

    private static final String TOKEN = "token-de-prueba-del-scheduler";

    @Mock
    private SyncService syncService;

    @Test
    void conTokenDisparaUnaSincronizacionCompletaSemanal() {
        SyncScheduler scheduler = scheduler(TOKEN);

        scheduler.runWeeklySync();

        verify(syncService).synchronizeAll(SyncOrigin.WEEKLY);
    }

    @Test
    void sinTokenNoLlamaAlServicioYDejaUnInfo(CapturedOutput output) {
        SyncScheduler scheduler = scheduler(null);

        scheduler.runWeeklySync();

        verifyNoInteractions(syncService);
        assertLine(output, "Se omite la sincronización semanal")
                .contains("INFO")
                .contains("falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN)");
    }

    @Test
    void conOtraEnCursoNoPropagaLaExcepcionYDejaUnWarnConElMotivo(CapturedOutput output) {
        when(syncService.synchronizeAll(SyncOrigin.WEEKLY)).thenThrow(new SyncInProgressException());
        SyncScheduler scheduler = scheduler(TOKEN);

        assertThatCode(scheduler::runWeeklySync).doesNotThrowAnyException();
        assertLine(output, "Se omite la sincronización semanal")
                .contains("WARN")
                .contains("Ya hay una sincronización en curso.");
    }

    @Test
    void laCorridaSemanalTomaElCronYLaZonaDeLaConfiguracion() throws NoSuchMethodException {
        Scheduled scheduled = SyncScheduler.class.getMethod("runWeeklySync").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${futbolmarket.football-data.sync.cron}");
        assertThat(scheduled.zone()).isEqualTo("${futbolmarket.football-data.sync.zone}");
    }

    private SyncScheduler scheduler(String token) {
        return new SyncScheduler(new FootballDataProperties(token, URI.create("http://football-data.invalid/v4"),
                Duration.ofSeconds(10), Duration.ofSeconds(30),
                new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", false)), syncService);
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }
}
