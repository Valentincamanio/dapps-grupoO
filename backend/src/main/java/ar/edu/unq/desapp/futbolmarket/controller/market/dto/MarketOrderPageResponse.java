package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;

/**
 * Una hoja del listado del mercado (esquema {@code MarketOrderPage}), con la paginación del
 * catálogo.
 */
public record MarketOrderPageResponse(List<MarketOrderResponse> content, int page, int size, long totalElements,
                                      int totalPages, boolean hasPrevious, boolean hasNext) {

    public static MarketOrderPageResponse from(QuotePage<OrderView> orders) {
        return new MarketOrderPageResponse(
                orders.content().stream().map(MarketOrderResponse::from).toList(),
                orders.page(),
                orders.size(),
                orders.totalElements(),
                orders.totalPages(),
                orders.hasPrevious(),
                orders.hasNext());
    }
}
