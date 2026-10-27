package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando se pide un recálculo mientras corre una sincronización: las cotizaciones no se
 * calculan con estadísticas a medio cargar (D17). Responde 409.
 */
public class RecalculationBlockedBySyncException extends ConflictException {

    private static final String MESSAGE = "No se puede recalcular: hay una sincronización en curso.";

    public RecalculationBlockedBySyncException() {
        super(MESSAGE);
    }
}
