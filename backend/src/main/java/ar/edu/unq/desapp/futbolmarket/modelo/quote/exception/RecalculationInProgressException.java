package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando se pide un recálculo con otro en curso: no se espera ni se encola (D17).
 * Responde 409.
 */
public class RecalculationInProgressException extends ConflictException {

    private static final String MESSAGE = "Ya hay un recálculo en curso.";

    public RecalculationInProgressException() {
        super(MESSAGE);
    }
}
