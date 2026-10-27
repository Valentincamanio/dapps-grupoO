package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Una semana cerrada que quedó sin cotizar en la corrida, con su motivo, para el informe.
 */
public record PendingWeek(QuoteWeek week, PendingReason reason) {
    public PendingWeek {
        if (week == null || reason == null) {
            throw new QuoteInvariantException("Una semana pendiente necesita la semana y el motivo.");
        }
    }
}
