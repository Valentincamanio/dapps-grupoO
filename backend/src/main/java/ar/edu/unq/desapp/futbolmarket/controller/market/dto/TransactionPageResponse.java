package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;

/**
 * Una hoja del historial de un usuario (esquema {@code TransactionPage}), con la paginación del
 * catálogo.
 */
public record TransactionPageResponse(List<TransactionResponse> content, int page, int size, long totalElements,
                                      int totalPages, boolean hasPrevious, boolean hasNext) {

    public static TransactionPageResponse from(QuotePage<TradeView> trades, long userId) {
        return new TransactionPageResponse(
                trades.content().stream().map(view -> TransactionResponse.from(view, userId)).toList(),
                trades.page(),
                trades.size(),
                trades.totalElements(),
                trades.totalPages(),
                trades.hasPrevious(),
                trades.hasNext());
    }
}
