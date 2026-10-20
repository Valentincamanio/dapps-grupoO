package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;

/**
 * Una hoja del ranking con la estrategia activa y la paginación del catálogo.
 */
public record RankingPageResponse(StrategyRefResponse activeStrategy, List<RankingRowResponse> content, int page,
                                  int size, long totalElements, int totalPages, boolean hasPrevious,
                                  boolean hasNext) {

    public static RankingPageResponse from(RankingPage ranking) {
        QuotePage<RankedQuote> page = ranking.page();
        return new RankingPageResponse(
                StrategyRefResponse.from(ranking.activeStrategy()),
                page.content().stream().map(RankingRowResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }
}
