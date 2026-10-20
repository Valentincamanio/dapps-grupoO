package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Qué semanas cerradas se cotizan y cuáles quedan pendientes (research D11). Es una función pura
 * de lo que ya se leyó: no consulta nada.
 *
 * <ol>
 *     <li>Desde la semana siguiente a la última cotizada; si no hay ninguna, desde la primera con
 *     estadísticas. Sin estadísticas en ninguna parte, no hay nada para cotizar (FR-010).</li>
 *     <li>Hasta la última semana cerrada; la semana en curso nunca (FR-008).</li>
 *     <li>Una semana con partidos terminados sin estadísticas espera {@link #STATS_WAIT} desde su
 *     cierre; pasado ese plazo se cotiza con lo que haya (FR-011).</li>
 *     <li>Una semana pendiente deja pendientes a las siguientes (FR-011).</li>
 *     <li>Una semana sin partidos se cotiza: nadie cambia de valor (SC-004).</li>
 * </ol>
 */
public record QuotingPlan(List<WeekDecision> decisions) {
    public static final Duration STATS_WAIT = Duration.ofDays(7);

    public QuotingPlan {
        decisions = List.copyOf(decisions);
    }

    public static QuotingPlan of(Optional<QuoteWeek> lastQuoted, Optional<QuoteWeek> firstWithStats,
                                 List<Match> finishedMatches, Instant now) {
        return of(lastQuoted, firstWithStats, finishedMatches, now, STATS_WAIT);
    }

    /**
     * Con una espera distinta de {@link #STATS_WAIT}. Con la de 7 días, la semana siguiente a una
     * pendiente cierra justo cuando vence la espera, así que la regla 4 no se puede ver; esta
     * variante existe para probarla.
     */
    static QuotingPlan of(Optional<QuoteWeek> lastQuoted, Optional<QuoteWeek> firstWithStats,
                          List<Match> finishedMatches, Instant now, Duration statsWait) {
        if (now == null) {
            throw new QuoteInvariantException("El instante del plan es obligatorio.");
        }
        Optional<QuoteWeek> from = lastQuoted.map(QuoteWeek::next).or(() -> firstWithStats);
        if (from.isEmpty()) {
            return new QuotingPlan(List.of());
        }
        var weeksMissingStats = weeksMissingStats(finishedMatches);
        var lastClosed = QuoteWeek.lastClosedAt(now);
        var decisions = new ArrayList<WeekDecision>();
        var blocked = false;
        for (var week = from.get(); week.compareTo(lastClosed) <= 0; week = week.next()) {
            var decision = decide(week, blocked, weeksMissingStats, now, statsWait);
            blocked = decision.isPending();
            decisions.add(decision);
        }
        return new QuotingPlan(decisions);
    }

    /**
     * La semana del partido terminado más temprano que ya tiene estadísticas: desde ahí se cotiza
     * cuando todavía no hay ninguna cotización (research D11).
     */
    public static Optional<QuoteWeek> firstWeekWithStats(List<Match> finishedMatches) {
        return finishedMatches.stream()
                .filter(Match::statsLoaded)
                .map(Match::utcDate)
                .min(Comparator.naturalOrder())
                .map(QuoteWeek::containing);
    }

    /**
     * Las semanas que se cotizan, en orden cronológico.
     */
    public List<QuoteWeek> weeksToQuote() {
        return decisions.stream().filter(decision -> !decision.isPending()).map(WeekDecision::week).toList();
    }

    /**
     * Las semanas que quedan pendientes, en orden, con su motivo.
     */
    public List<PendingWeek> pendingWeeks() {
        return decisions.stream()
                .filter(WeekDecision::isPending)
                .map(decision -> new PendingWeek(decision.week(), decision.pendingReason()))
                .toList();
    }

    /**
     * Si queda alguna semana cerrada sin cotizar, se pueda cotizar ya o no. Con esto el arranque
     * decide si dispara el recálculo.
     */
    public boolean hasClosedUnquotedWeeks() {
        return !decisions.isEmpty();
    }

    private static WeekDecision decide(QuoteWeek week, boolean previousPending, Set<QuoteWeek> weeksMissingStats,
                                       Instant now, Duration statsWait) {
        if (previousPending) {
            return WeekDecision.pending(week, PendingReason.PREVIOUS_WEEK_PENDING);
        }
        if (weeksMissingStats.contains(week) && stillWaitingForStats(week, now, statsWait)) {
            return WeekDecision.pending(week, PendingReason.MISSING_STATS);
        }
        return WeekDecision.quote(week);
    }

    private static boolean stillWaitingForStats(QuoteWeek week, Instant now, Duration statsWait) {
        return now.isBefore(week.end().plus(statsWait));
    }

    /**
     * Las semanas con algún partido terminado al que le faltan las estadísticas, agrupadas una sola
     * vez para no recorrer los partidos por cada semana.
     */
    private static Set<QuoteWeek> weeksMissingStats(List<Match> finishedMatches) {
        return finishedMatches.stream()
                .filter(Match::needsStats)
                .map(match -> QuoteWeek.containing(match.utcDate()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
