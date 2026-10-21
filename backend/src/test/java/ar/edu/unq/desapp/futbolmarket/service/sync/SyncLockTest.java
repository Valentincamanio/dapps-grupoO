package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;

class SyncLockTest {

    private final SyncLock lock = new SyncLock();

    @Test
    void devuelveElValorDeLaSincronizacion() {
        String value = lock.runExclusively(() -> "informe");

        assertThat(value).isEqualTo("informe");
    }

    @Test
    void unSegundoPedidoMientrasCorreLaPrimeraSeRechazaSinEsperar() {
        Throwable inner = lock.runExclusively(() -> catchThrowable(() -> lock.runExclusively(() -> "segunda")));

        assertThat(inner)
                .isInstanceOf(SyncInProgressException.class)
                .hasMessage("Ya hay una sincronización en curso.");
    }

    @Test
    void seLiberaAlTerminar() {
        lock.runExclusively(() -> "primera");

        String second = lock.runExclusively(() -> "segunda");

        assertThat(second).isEqualTo("segunda");
    }

    @Test
    void seLiberaTambienDespuesDeUnaExcepcion() {
        assertThatThrownBy(() -> lock.runExclusively(() -> {
            throw new IllegalStateException("falla inesperada");
        })).isInstanceOf(IllegalStateException.class);

        String next = lock.runExclusively(() -> "siguiente");

        assertThat(next).isEqualTo("siguiente");
    }
}
