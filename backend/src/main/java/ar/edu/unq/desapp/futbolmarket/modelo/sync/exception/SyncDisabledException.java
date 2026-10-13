package ar.edu.unq.desapp.futbolmarket.modelo.sync.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ServiceUnavailableException;

/**
 * Se lanza cuando se pide una sincronización sin la credencial de la fuente configurada. Responde
 * 503 (FR-041).
 */
public class SyncDisabledException extends ServiceUnavailableException {

    private static final String MESSAGE = "La sincronización con Football-Data.org está deshabilitada: "
            + "falta configurar la credencial de la fuente (FOOTBALL_DATA_TOKEN).";

    public SyncDisabledException() {
        super(MESSAGE);
    }
}
