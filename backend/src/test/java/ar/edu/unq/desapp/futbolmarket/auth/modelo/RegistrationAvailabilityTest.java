package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameAndEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;

class RegistrationAvailabilityTest {

    @Test
    void noLanzaNadaCuandoLosDosEstanLibres() {
        assertThatCode(() -> new RegistrationAvailability(false, false).ensureAvailable())
                .doesNotThrowAnyException();
    }

    @Test
    void lanzaDuplicateUsernameCuandoSoloElNombreDeUsuarioEstaTomado() {
        assertThatThrownBy(() -> new RegistrationAvailability(true, false).ensureAvailable())
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void lanzaDuplicateEmailCuandoSoloElCorreoEstaTomado() {
        assertThatThrownBy(() -> new RegistrationAvailability(false, true).ensureAvailable())
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void lanzaDuplicateUsernameAndEmailCuandoLosDosEstanTomados() {
        assertThatThrownBy(() -> new RegistrationAvailability(true, true).ensureAvailable())
                .isInstanceOf(DuplicateUsernameAndEmailException.class);
    }
}
