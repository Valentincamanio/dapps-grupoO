package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import lombok.RequiredArgsConstructor;

/**
 * Al arrancar con el catálogo vacío, dispara una sincronización completa en segundo plano
 * (FR-033 y research D13). Corre cuando la aplicación ya levantó, en un hilo del executor de Boot,
 * así no demora el arranque.
 *
 * <p>Sin token, escribe la advertencia de arranque y termina: la aplicación levanta igual con la
 * sincronización deshabilitada (FR-041). Con {@code sync.on-startup} en {@code false}, como en el
 * perfil test, termina sin consultar el catálogo.</p>
 */
@Component
@RequiredArgsConstructor
public class StartupSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSync.class);

    private final FootballDataProperties properties;
    private final PlayerRepository playerRepository;
    private final SyncService syncService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeIfCatalogIsEmpty() {
        if (isNeeded()) {
            synchronize();
        }
    }

    private boolean isNeeded() {
        if (!properties.hasToken()) {
            LOGGER.warn("Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): "
                    + "la sincronización queda deshabilitada.");
            return false;
        }
        if (!properties.sync().onStartup()) {
            return false;
        }
        if (playerRepository.hasPlayers()) {
            LOGGER.info("El catálogo ya tiene jugadores: no se sincroniza al arrancar.");
            return false;
        }
        return true;
    }

    /**
     * Solo hay otra en curso si un administrador se adelantó: esta se omite.
     */
    private void synchronize() {
        LOGGER.info("El catálogo está vacío: se dispara una sincronización completa en segundo plano.");
        try {
            syncService.synchronizeAll(SyncOrigin.STARTUP);
        } catch (SyncInProgressException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite la sincronización de arranque: {}", reason);
        }
    }
}
