package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;

/**
 * Una hoja de las órdenes de un usuario (esquema {@code UserOrderPage}), con la paginación del
 * catálogo.
 */
public record UserOrderPageResponse(List<UserOrderResponse> content, int page, int size, long totalElements,
                                    int totalPages, boolean hasPrevious, boolean hasNext) {

    public static UserOrderPageResponse from(QuotePage<OrderView> orders) {
        return new UserOrderPageResponse(
                orders.content().stream().map(view -> UserOrderResponse.from(view.order(), view.player())).toList(),
                orders.page(),
                orders.size(),
                orders.totalElements(),
                orders.totalPages(),
                orders.hasPrevious(),
                orders.hasNext());
    }
}
