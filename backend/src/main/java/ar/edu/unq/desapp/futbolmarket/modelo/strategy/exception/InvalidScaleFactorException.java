package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando el factor de escala de una versión no es válido. Responde 400.
 */
public class InvalidScaleFactorException extends BadRequestException {
    public InvalidScaleFactorException(String message) {
        super(message);
    }
}
