package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza al cambiar los potenciados de una semana que ya cerró: quedan como registro. Responde 409.
 */
public class ClosedBoostWeekException extends ConflictException {
    public ClosedBoostWeekException(QuoteWeek week) {
        super("La semana del " + week.monday() + " ya cerró: sus potenciados no se pueden cambiar.");
    }
}
