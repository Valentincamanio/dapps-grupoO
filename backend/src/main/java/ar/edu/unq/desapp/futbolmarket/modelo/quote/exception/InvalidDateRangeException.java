package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando el inicio de un rango de fechas es posterior a su final. Responde 400.
 */
public class InvalidDateRangeException extends BadRequestException {

    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        super("El inicio del rango (" + from + ") es posterior al final (" + to + ").");
    }
}
