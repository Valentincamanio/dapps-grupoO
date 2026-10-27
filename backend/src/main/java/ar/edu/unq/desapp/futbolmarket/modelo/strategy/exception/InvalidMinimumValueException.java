package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando la cotización mínima de una versión no es válida. Responde 400.
 */
public class InvalidMinimumValueException extends BadRequestException {
    public InvalidMinimumValueException(String message) {
        super(message);
    }
}
