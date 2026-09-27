package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando el nombre de usuario ya está tomado. Responde 409.
 */
public class DuplicateUsernameException extends ConflictException {

    private static final String MESSAGE = "El nombre de usuario ya está registrado.";

    public DuplicateUsernameException() {
        super(MESSAGE);
    }
}
