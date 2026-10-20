package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;

/**
 * Una hoja de la evolución de un jugador, ordenada por vigencia ascendente, con la paginación del
 * catálogo.
 */
public record QuoteHistoryPageResponse(long playerId, List<QuoteResponse> content, int page, int size,
                                       long totalElements, int totalPages, boolean hasPrevious, boolean hasNext) {

    public static QuoteHistoryPageResponse from(long playerId, QuotePage<Quote> history) {
        return new QuoteHistoryPageResponse(
                playerId,
                history.content().stream().map(QuoteResponse::from).toList(),
                history.page(),
                history.size(),
                history.totalElements(),
                history.totalPages(),
                history.hasPrevious(),
                history.hasNext());
    }
}
