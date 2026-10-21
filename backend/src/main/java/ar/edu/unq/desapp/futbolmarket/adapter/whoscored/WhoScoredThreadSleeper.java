package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.time.Duration;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInterruptedException;

/**
 * Duerme el hilo de la corrida. Su nombre es distinto del {@code ThreadSleeper} de Football-Data a
 * propósito: dos beans con el mismo nombre impedirían arrancar la aplicación (research D6).
 */
@Component
public class WhoScoredThreadSleeper implements WhoScoredSleeper {

    /**
     * Si el hilo se interrumpe, porque la aplicación se está apagando, restaura la marca de
     * interrupción y termina la corrida. Una espera de cero o negativa no duerme.
     */
    @Override
    public void sleep(Duration duration) {
        if (!duration.isPositive()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncInterruptedException(e);
        }
    }
}
