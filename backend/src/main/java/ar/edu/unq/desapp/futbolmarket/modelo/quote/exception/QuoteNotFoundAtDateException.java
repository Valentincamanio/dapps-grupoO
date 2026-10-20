package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

/**
 * Se lanza cuando el jugador existe pero ninguna de sus cotizaciones regía al final de ese día: la
 * fecha es anterior a su primera cotización (FR-043). Responde 404.
 */
public class QuoteNotFoundAtDateException extends NotFoundException {

    public QuoteNotFoundAtDateException(long playerId, LocalDate date) {
        super("El jugador " + playerId + " no tiene cotización al " + date + ".");
    }
}
