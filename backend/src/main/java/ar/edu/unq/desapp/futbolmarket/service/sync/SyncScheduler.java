package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import lombok.RequiredArgsConstructor;

/**
 * La cadena semanal: los lunes a las 04:00 de Argentina, Football-Data y después WhoScored, según
 * el cron y la zona de la configuración (FR-033 y research D22). Con el cron {@code "-"}, como en
 * el perfil test, la tarea no se programa.
 *
 * <p>Una corrida perdida, por ejemplo con la aplicación apagada, no se recupera acá: la recupera la
 * puesta al día al arrancar, según la última completa exitosa de cada fuente.</p>
 */
@Component
@RequiredArgsConstructor
public class SyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncCoordinator coordinator;

    /**
     * Sin token, la cadena saltea Football-Data y corre WhoScored igual. Si hay otra en curso, esta
     * se omite: no se encola ni se reintenta (FR-041).
     */
    @Scheduled(cron = "${futbolmarket.football-data.sync.cron}", zone = "${futbolmarket.football-data.sync.zone}")
    public void runWeeklySync() {
        try {
            coordinator.runWeeklyChain();
        } catch (SyncInProgressException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite la sincronización semanal: {}", reason);
        }
    }
}
