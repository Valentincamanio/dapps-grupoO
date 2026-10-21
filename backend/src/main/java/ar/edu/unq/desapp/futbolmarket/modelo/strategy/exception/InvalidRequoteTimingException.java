package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando la opción de recotización no es {@code AHORA} ni {@code PROXIMA_SEMANA}.
 * Responde 400 con el valor recibido.
 */
public class InvalidRequoteTimingException extends BadRequestException {
    public InvalidRequoteTimingException(String value) {
        super("La opción '" + value + "' no es válida: use AHORA o PROXIMA_SEMANA.");
    }
}
