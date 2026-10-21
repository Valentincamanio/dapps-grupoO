package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInterruptedException;

class WhoScoredThreadSleeperTest {

    private final WhoScoredThreadSleeper sleeper = new WhoScoredThreadSleeper();

    /**
     * Ningún test deja el hilo interrumpido para el siguiente, aunque falle.
     */
    @AfterEach
    void clearInterruption() {
        Thread.interrupted();
    }

    @Test
    void conElHiloInterrumpidoTerminaLaCorridaYConservaLaMarcaDeInterrupcion() {
        Thread.currentThread().interrupt();

        Throwable thrown = catchThrowable(() -> sleeper.sleep(Duration.ofSeconds(1)));

        assertThat(thrown)
                .isInstanceOf(SyncInterruptedException.class)
                .hasMessage("Se interrumpió la espera entre pedidos a WhoScored.")
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void unaEsperaDeCeroTerminaSinLanzar() {
        assertThatCode(() -> sleeper.sleep(Duration.ZERO)).doesNotThrowAnyException();
    }
}
