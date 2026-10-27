package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;
import lombok.RequiredArgsConstructor;

/**
 * El candado del recálculo (research D17): un recálculo a la vez, y nunca junto con una
 * sincronización de cualquiera de las dos fuentes.
 *
 * <p>Toma primero su semáforo y después el {@link SyncLock} de la 006; los suelta al revés. No
 * espera ni encola: un segundo recálculo se rechaza con {@link RecalculationInProgressException} y
 * uno pedido durante una sincronización, con {@link RecalculationBlockedBySyncException} (los dos
 * 409). Mientras corre un recálculo, una sincronización recibe el 409 de la 006 (P-2 de D18).</p>
 */
@Component
@RequiredArgsConstructor
public class RecalculationLock {
    private final Semaphore permit = new Semaphore(1);
    private final SyncLock syncLock;

    public <T> T runExclusively(Supplier<T> action) {
        if (!permit.tryAcquire()) {
            throw new RecalculationInProgressException();
        }
        var started = new AtomicBoolean(false);
        try {
            return syncLock.runExclusively(() -> {
                started.set(true);
                return action.get();
            });
        } catch (SyncInProgressException e) {
            throw started.get() ? e : new RecalculationBlockedBySyncException();
        } finally {
            permit.release();
        }
    }
}
