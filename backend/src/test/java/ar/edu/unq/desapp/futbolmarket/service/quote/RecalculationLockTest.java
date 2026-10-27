package ar.edu.unq.desapp.futbolmarket.service.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;

class RecalculationLockTest {

    private final SyncLock syncLock = new SyncLock();
    private final RecalculationLock lock = new RecalculationLock(syncLock);

    @Test
    void conLosDosCandadosLibresCorreLaAccionYDevuelveSuValor() {
        String value = lock.runExclusively(() -> "informe");

        assertThat(value).isEqualTo("informe");
    }

    @Test
    void mientrasCorreLaAccionLaSincronizacionTambienQuedaTomada() {
        Throwable sync = lock.runExclusively(() -> catchThrowable(() -> syncLock.runExclusively(() -> "sincronizar")));

        assertThat(sync).isInstanceOf(SyncInProgressException.class);
    }

    @Test
    void unSegundoRecalculoMientrasCorreElPrimeroSeRechazaSinEsperar() {
        Throwable inner = lock.runExclusively(() -> catchThrowable(() -> lock.runExclusively(() -> "segundo")));

        assertThat(inner)
                .isInstanceOf(RecalculationInProgressException.class)
                .hasMessage("Ya hay un recálculo en curso.");
    }

    @Test
    void conUnaSincronizacionEnCursoSeRechazaConSuPropioMotivo() {
        Throwable inner = syncLock.runExclusively(() -> catchThrowable(() -> lock.runExclusively(() -> "recalcular")));

        assertThat(inner)
                .isInstanceOf(RecalculationBlockedBySyncException.class)
                .hasMessage("No se puede recalcular: hay una sincronización en curso.");
    }

    @Test
    void rechazadoPorUnaSincronizacionNoQuedaTomadoElCandadoDelRecalculo() {
        syncLock.runExclusively(() -> catchThrowable(() -> lock.runExclusively(() -> "recalcular")));

        String next = lock.runExclusively(() -> "siguiente");

        assertThat(next).isEqualTo("siguiente");
    }

    @Test
    void losDosCandadosQuedanLibresDespuesDeUnaExcepcionEnLaAccion() {
        assertThatThrownBy(() -> lock.runExclusively(() -> {
            throw new IllegalStateException("falla inesperada");
        })).isInstanceOf(IllegalStateException.class);

        String recalculation = lock.runExclusively(() -> "recálculo");
        String sync = syncLock.runExclusively(() -> "sincronización");

        assertThat(recalculation).isEqualTo("recálculo");
        assertThat(sync).isEqualTo("sincronización");
    }

    @Test
    void unaSincronizacionRechazadaDentroDeLaAccionNoSeTraduceAlMotivoDelRecalculo() {
        Throwable thrown = catchThrowable(() -> lock.runExclusively(() -> syncLock.runExclusively(() -> "anidada")));

        assertThat(thrown).isExactlyInstanceOf(SyncInProgressException.class);
    }
}
