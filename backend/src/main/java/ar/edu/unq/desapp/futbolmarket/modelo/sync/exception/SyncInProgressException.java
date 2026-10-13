package ar.edu.unq.desapp.futbolmarket.modelo.sync.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando se pide una sincronización con otra en curso. Responde 409.
 */
public class SyncInProgressException extends ConflictException {

    private static final String MESSAGE = "Ya hay una sincronización en curso.";

    public SyncInProgressException() {
        super(MESSAGE);
    }
}
