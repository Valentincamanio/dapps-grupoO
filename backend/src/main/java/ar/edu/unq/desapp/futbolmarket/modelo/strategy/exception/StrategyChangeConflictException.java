package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando guardar un cambio de estrategia choca con un índice único porque otro cambio se
 * cruzó con este (research D10). La transacción se deshace entera, así que la activa no cambia.
 * Responde 409.
 */
public class StrategyChangeConflictException extends ConflictException {

    private static final String MESSAGE = "La estrategia cambió mientras se guardaba: reintente.";

    public StrategyChangeConflictException() {
        super(MESSAGE);
    }
}
