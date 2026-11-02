package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;

/**
 * Una orden del listado público (esquema {@code MarketOrder}, FR-049). A propósito no lleva ningún
 * dato del dueño, ni su id: solo su tipo (FR-048 y SC-013).
 */
public record MarketOrderResponse(Long id, PlayerRefResponse player, OrderSide side, OwnerType ownerType,
                                  OrderStatus status, int requestedQuantity, int executedQuantity,
                                  int pendingQuantity, Instant createdAt) {

    public static MarketOrderResponse from(OrderView view) {
        MarketOrder order = view.order();
        return new MarketOrderResponse(order.getId(), PlayerRefResponse.from(view.player()), order.getSide(),
                order.ownerType(), order.status(), order.getRequested(), order.getExecuted(), order.pending(),
                order.getCreatedAt());
    }
}
