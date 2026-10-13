package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import java.time.Duration;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;

@Component
public class ThreadSleeper implements Sleeper {
    private static final String INTERRUPTED_REASON = "Se interrumpió la espera por el límite de consultas de la fuente.";

    /**
     * Si el hilo se interrumpe, restaura la marca de interrupción y la liga queda fallida (research
     * D5 y D6).
     */
    @Override
    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalSourceException(INTERRUPTED_REASON, e);
        }
    }
}
