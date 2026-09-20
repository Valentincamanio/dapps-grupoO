package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

/**
 * Se lanza cuando el nombre de usuario ya está tomado. Responde 409.
 */
public class DuplicateUsernameException extends RuntimeException {

    private static final String MESSAGE = "El nombre de usuario ya está registrado.";

    public DuplicateUsernameException() {
        super(MESSAGE);
    }
}
