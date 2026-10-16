package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;

/**
 * El candado único de las sincronizaciones de las dos fuentes (research D22): hay a lo sumo una a
 * la vez, sea de Football-Data, de WhoScored o la cadena (FR-041).
 *
 * <p>No espera ni encola: un segundo pedido se rechaza con {@link SyncInProgressException} (409).
 * El permiso no está atado al hilo, así que quien ya lo tiene no puede volver a tomarlo.</p>
 */
@Component
public class SyncLock {
    private final Semaphore permit = new Semaphore(1);

    public <T> T runExclusively(Supplier<T> action) {
        if (!permit.tryAcquire()) {
            throw new SyncInProgressException();
        }
        try {
            return action.get();
        } finally {
            permit.release();
        }
    }
}
