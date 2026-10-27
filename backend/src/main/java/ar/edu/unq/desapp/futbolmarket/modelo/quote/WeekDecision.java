package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Qué se hace con una semana cerrada sin cotizar: se cotiza, o queda pendiente con su motivo. La
 * decide {@link QuotingPlan} y no se guarda.
 */
public record WeekDecision(QuoteWeek week, PendingReason pendingReason) {
    public WeekDecision {
        if (week == null) {
            throw new QuoteInvariantException("La semana de la decisión es obligatoria.");
        }
    }

    public static WeekDecision quote(QuoteWeek week) {
        return new WeekDecision(week, null);
    }

    public static WeekDecision pending(QuoteWeek week, PendingReason reason) {
        if (reason == null) {
            throw new QuoteInvariantException("Una semana pendiente necesita su motivo.");
        }
        return new WeekDecision(week, reason);
    }

    public boolean isPending() {
        return pendingReason != null;
    }

    public Optional<PendingReason> reason() {
        return Optional.ofNullable(pendingReason);
    }
}
