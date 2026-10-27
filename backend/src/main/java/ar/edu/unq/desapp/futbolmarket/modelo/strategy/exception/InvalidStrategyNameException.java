package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando el nombre de una estrategia nueva falta o es demasiado largo. Responde 400.
 */
public class InvalidStrategyNameException extends BadRequestException {
    public InvalidStrategyNameException(String message) {
        super(message);
    }
}
