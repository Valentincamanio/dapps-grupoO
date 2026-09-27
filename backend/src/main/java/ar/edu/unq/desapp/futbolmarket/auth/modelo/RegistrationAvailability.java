package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameAndEmailException;

/**
 * Disponibilidad del nombre de usuario y del correo al registrarse.
 *
 * <p>La decisión de qué conflicto informar vive acá y no en el servicio, que queda sin ningún
 * {@code if} de negocio (FR-007).</p>
 */
public record RegistrationAvailability(boolean usernameTaken, boolean emailTaken) {

    public void ensureAvailable() {
        if (usernameTaken && emailTaken) {
            throw new DuplicateUsernameAndEmailException();
        }
        if (usernameTaken) {
            throw new DuplicateUsernameException();
        }
        if (emailTaken) {
            throw new DuplicateEmailException();
        }
    }
}
