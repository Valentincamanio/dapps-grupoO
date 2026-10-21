package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando una fecha de consulta falta o no tiene el formato {@code AAAA-MM-DD}. El mensaje
 * repite el valor recibido (research D20). Responde 400.
 */
public class InvalidQuoteDateException extends BadRequestException {

    private static final String MISSING_MESSAGE = "La fecha es obligatoria: use el formato AAAA-MM-DD.";

    public InvalidQuoteDateException(String value) {
        super(value == null ? MISSING_MESSAGE : "La fecha '" + value + "' no es válida: use el formato AAAA-MM-DD.");
    }
}
