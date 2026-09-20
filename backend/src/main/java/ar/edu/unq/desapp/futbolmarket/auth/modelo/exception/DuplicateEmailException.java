package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

/**
 * Se lanza cuando el correo electrónico ya está tomado. Responde 409.
 */
public class DuplicateEmailException extends RuntimeException {

    private static final String MESSAGE = "El correo electrónico ya está registrado.";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
