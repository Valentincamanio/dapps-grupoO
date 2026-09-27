package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando el nombre de usuario y el correo electrónico ya están tomados. Responde 409.
 */
public class DuplicateUsernameAndEmailException extends ConflictException {

    private static final String MESSAGE = "El nombre de usuario y el correo electrónico ya están registrados.";

    public DuplicateUsernameAndEmailException() {
        super(MESSAGE);
    }
}
