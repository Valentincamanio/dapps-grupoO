package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando el cambio de contraseña no se puede aplicar: la actual no coincide, la nueva
 * es igual a la actual o la nueva no cumple el formato. Responde 400.
 */
public class InvalidPasswordChangeException extends BadRequestException {

    public InvalidPasswordChangeException(String message) {
        super(message);
    }
}
