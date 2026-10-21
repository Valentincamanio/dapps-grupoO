package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidRequoteTimingException;
import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequoteTimingTest {

    @Test
    void ahoraEsRecotizarEnElActo() {
        assertThat(RequoteTiming.fromWire("AHORA")).isEqualTo(RequoteTiming.NOW);
    }

    @Test
    void proximaSemanaEsSoloCambiarLaActiva() {
        assertThat(RequoteTiming.fromWire("PROXIMA_SEMANA")).isEqualTo(RequoteTiming.NEXT_WEEK);
    }

    @Test
    void sinOpcionRigeDesdeLaSemanaSiguiente() {
        assertThat(RequoteTiming.fromWire(null)).isEqualTo(RequoteTiming.NEXT_WEEK);
    }

    @Test
    void cadaOpcionConoceSuValorDeLaApi() {
        assertThat(RequoteTiming.NOW.wireValue()).isEqualTo("AHORA");
        assertThat(RequoteTiming.NEXT_WEEK.wireValue()).isEqualTo("PROXIMA_SEMANA");
    }

    @Test
    void unValorDesconocidoSeRechazaConBadRequestIndicandoElValor() {
        assertThatThrownBy(() -> RequoteTiming.fromWire("AHORA2"))
                .isInstanceOf(InvalidRequoteTimingException.class)
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La opción 'AHORA2' no es válida: use AHORA o PROXIMA_SEMANA.");
    }

    @Test
    void losNombresDeJavaNoSonValoresDeLaApi() {
        assertThatThrownBy(() -> RequoteTiming.fromWire("NOW"))
                .isInstanceOf(InvalidRequoteTimingException.class)
                .hasMessage("La opción 'NOW' no es válida: use AHORA o PROXIMA_SEMANA.");
        assertThatThrownBy(() -> RequoteTiming.fromWire("ahora"))
                .isInstanceOf(InvalidRequoteTimingException.class);
    }
}
