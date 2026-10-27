package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando se pide la cotización de un día que todavía no llegó, en hora de Argentina.
 * Responde 400.
 */
public class FutureQuoteDateException extends BadRequestException {

    public FutureQuoteDateException(LocalDate date) {
        super("La fecha " + date + " es futura.");
    }
}
