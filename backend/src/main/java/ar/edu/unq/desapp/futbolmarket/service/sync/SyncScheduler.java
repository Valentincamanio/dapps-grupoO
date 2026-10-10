package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import lombok.RequiredArgsConstructor;

/**
 * La sincronización semanal: los lunes a las 04:00 de Argentina, según el cron y la zona de la
 * configuración (FR-026 y research D12). Con el cron {@code "-"}, como en el perfil test, la tarea
 * no se programa.
 *
 * <p>Una corrida perdida, por ejemplo con la aplicación apagada, no se recupera: el cron calcula la
 * próxima a partir del momento actual (FR-027).</p>
 */
@Component
@RequiredArgsConstructor
public class SyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncScheduler.class);

    private final FootballDataProperties properties;
    private final SyncService syncService;

    /**
     * Sin token no hay nada que sincronizar (FR-041). Si hay otra en curso, esta se omite: no se
     * encola ni se reintenta (FR-031).
     */
    @Scheduled(cron = "${futbolmarket.football-data.sync.cron}", zone = "${futbolmarket.football-data.sync.zone}")
    public void runWeeklySync() {
        if (!properties.hasToken()) {
            LOGGER.info("Se omite la sincronización semanal: falta la credencial de Football-Data.org "
                    + "(FOOTBALL_DATA_TOKEN).");
            return;
        }
        try {
            syncService.synchronizeAll(SyncOrigin.WEEKLY);
        } catch (SyncInProgressException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite la sincronización semanal: {}", reason);
        }
    }
}
