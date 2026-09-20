package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

/**
 * Se lanza cuando el cambio de contraseña no se puede aplicar: la actual no coincide, la nueva
 * es igual a la actual o la nueva no cumple el formato. Responde 400.
 */
public class InvalidPasswordChangeException extends RuntimeException {

    public InvalidPasswordChangeException(String message) {
        super(message);
    }
}
