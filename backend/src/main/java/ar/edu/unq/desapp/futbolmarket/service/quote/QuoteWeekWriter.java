package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotingPlan;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeeklyQuotation;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import lombok.RequiredArgsConstructor;

/**
 * Escribe una semana de cotizaciones en una transacción: se guarda completa o no queda nada
 * (FR-012, SC-016). Qué cotizaciones nacen lo decide {@link WeeklyQuotation}; acá solo se lee y se
 * guarda.
 */
@Service
@RequiredArgsConstructor
public class QuoteWeekWriter {

    private final PlayerRepository playerRepository;
    private final PlayerMatchStatsRepository statsRepository;
    private final QuoteRepository quoteRepository;
    private final MatchRepository matchRepository;

    @Transactional
    public WeekResult quoteWeek(QuoteWeek week, StrategyVersion strategy) {
        var activePlayers = playerRepository.findAllActive();
        var weekStats = statsRepository.findAllByMatchBetween(week.start(), week.end());
        var lastBeforeWeek = quoteRepository.findLastBeforeWeek(week);
        var quotes = WeeklyQuotation.quoteWeek(week, strategy, activePlayers, weekStats, lastBeforeWeek);
        return WeekResult.of(week, quoteRepository.saveAll(quotes));
    }

    /**
     * Recotiza en el acto con la estrategia nueva (research D12). Corre dentro de la transacción
     * del cambio de estrategia (D14), así que un error en cualquier paso deshace también el cambio
     * de la activa; sin esa transacción, falla.
     *
     * <ol>
     *     <li>Cotiza con la versión nueva las semanas cerradas pendientes, en orden y según
     *     {@link QuotingPlan}, como cualquier recálculo.</li>
     *     <li>Si la última semana cerrada tiene cotizaciones, suma las recotizadas de los jugadores
     *     que no la tienen cotizada con esa versión. Si sigue pendiente, queda en el informe con su
     *     motivo y no hay nada que recotizar.</li>
     * </ol>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requoteLastWeek(StrategyVersion strategy, Instant now, RecalculationReport.Builder report) {
        var finishedMatches = matchRepository.findAllFinished();
        var plan = QuotingPlan.of(quoteRepository.findLastCalculatedWeek(),
                QuotingPlan.firstWeekWithStats(finishedMatches), finishedMatches, now);
        report.pendingWeeks(plan.pendingWeeks());
        plan.weeksToQuote().forEach(week -> report.quotedWeek(quoteWeek(week, strategy)));

        var lastClosed = QuoteWeek.lastClosedAt(now);
        var latestOfWeek = quoteRepository.findLatestOfWeek(lastClosed);
        if (latestOfWeek.isEmpty()) {
            return;
        }
        var requoted = WeeklyQuotation.requoteWeek(lastClosed, strategy, playerRepository.findAllActive(),
                statsRepository.findAllByMatchBetween(lastClosed.start(), lastClosed.end()),
                quoteRepository.findLastBeforeWeek(lastClosed), latestOfWeek, now);
        if (!requoted.isEmpty()) {
            report.quotedWeek(WeekResult.of(lastClosed, quoteRepository.saveAll(requoted)));
        }
    }
}
