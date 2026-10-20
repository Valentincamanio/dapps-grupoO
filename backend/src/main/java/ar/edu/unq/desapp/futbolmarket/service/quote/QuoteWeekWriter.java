package ar.edu.unq.desapp.futbolmarket.service.quote;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeeklyQuotation;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
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

    @Transactional
    public WeekResult quoteWeek(QuoteWeek week, StrategyVersion strategy) {
        var activePlayers = playerRepository.findAllActive();
        var weekStats = statsRepository.findAllByMatchBetween(week.start(), week.end());
        var lastBeforeWeek = quoteRepository.findLastBeforeWeek(week);
        var quotes = WeeklyQuotation.quoteWeek(week, strategy, activePlayers, weekStats, lastBeforeWeek);
        return WeekResult.of(week, quoteRepository.saveAll(quotes));
    }
}
