package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * El informe de un recálculo (data-model, sección 3.7). Va al registro y a la respuesta del
 * disparo manual; no se guarda.
 *
 * <p>Si una semana falló al guardarse, {@code interruptedAt} la nombra: de esa semana no quedó
 * nada, las siguientes no se intentaron y la próxima corrida la reintenta. Un informe sin semanas
 * para cotizar no es un error (FR-052).</p>
 */
public record RecalculationReport(RecalculationOrigin origin, Instant startedAt, Instant finishedAt,
                                  StrategyVersionRef strategy, List<WeekResult> quotedWeeks,
                                  List<PendingWeek> pendingWeeks, Optional<QuoteWeek> interruptedAt) {
    public RecalculationReport {
        requireNotNull(origin, "El origen del recálculo es obligatorio.");
        requireNotNull(startedAt, "El inicio del recálculo es obligatorio.");
        requireNotNull(finishedAt, "El fin del recálculo es obligatorio.");
        requireNotNull(strategy, "La estrategia del recálculo es obligatoria.");
        quotedWeeks = List.copyOf(quotedWeeks);
        pendingWeeks = List.copyOf(pendingWeeks);
    }

    public static Builder builder(RecalculationOrigin origin, Instant startedAt, StrategyVersionRef strategy) {
        return new Builder(origin, startedAt, strategy);
    }

    /**
     * No había semanas para cotizar: ni se cotizó ninguna ni falló ninguna.
     */
    public boolean isEmpty() {
        return quotedWeeks.isEmpty() && interruptedAt.isEmpty();
    }

    public boolean wasInterrupted() {
        return interruptedAt.isPresent();
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new QuoteInvariantException(message);
        }
    }

    /**
     * Va acumulando lo que pasa mientras corre la corrida, semana por semana.
     */
    public static final class Builder {
        private final RecalculationOrigin origin;
        private final Instant startedAt;
        private final StrategyVersionRef strategy;
        private final List<WeekResult> quotedWeeks = new ArrayList<>();
        private final List<PendingWeek> pendingWeeks = new ArrayList<>();
        private QuoteWeek interruptedAt;

        private Builder(RecalculationOrigin origin, Instant startedAt, StrategyVersionRef strategy) {
            this.origin = origin;
            this.startedAt = startedAt;
            this.strategy = strategy;
        }

        public Builder quotedWeek(WeekResult result) {
            quotedWeeks.add(result);
            return this;
        }

        public Builder pendingWeeks(List<PendingWeek> pending) {
            pendingWeeks.addAll(pending);
            return this;
        }

        /**
         * La semana que falló al guardarse. Una corrida se corta en la primera que falla.
         */
        public Builder interruptedAt(QuoteWeek week) {
            if (interruptedAt != null) {
                throw new QuoteInvariantException("El recálculo ya se había interrumpido en " + interruptedAt + ".");
            }
            interruptedAt = week;
            return this;
        }

        public RecalculationReport build(Instant finishedAt) {
            return new RecalculationReport(origin, startedAt, finishedAt, strategy, quotedWeeks, pendingWeeks,
                    Optional.ofNullable(interruptedAt));
        }
    }
}
