package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import lombok.RequiredArgsConstructor;

/**
 * La puesta al día al arrancar (FR-034, FR-035 y FR-051; research D22). Corre cuando la aplicación
 * ya levantó, en un hilo del executor de Boot, así no demora el arranque. Qué se sincroniza lo
 * decide {@link SyncCoordinator} según la última completa exitosa de cada fuente.
 *
 * <p>Sin token, escribe la advertencia de arranque y sigue: la sincronización de Football-Data
 * queda deshabilitada, pero la de WhoScored no la necesita (HU3, escenario 8). Con
 * {@code sync.on-startup} en {@code false}, como en el perfil test, termina sin hacer nada.</p>
 */
@Component
@RequiredArgsConstructor
public class StartupSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSync.class);

    private final FootballDataProperties properties;
    private final SyncCoordinator coordinator;

    /**
     * Solo hay otra en curso si un administrador se adelantó: esta se omite.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        if (!properties.hasToken()) {
            LOGGER.warn("Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): "
                    + "la sincronización queda deshabilitada.");
        }
        if (!properties.sync().onStartup()) {
            return;
        }
        try {
            coordinator.catchUpOnStartup();
        } catch (SyncInProgressException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite la sincronización de arranque: {}", reason);
        }
    }
}
