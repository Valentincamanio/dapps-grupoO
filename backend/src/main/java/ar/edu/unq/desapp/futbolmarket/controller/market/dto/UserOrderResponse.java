package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Una orden vista por su dueño (esquema {@code UserOrder}): la del listado más lo cancelado y la
 * reserva. No informa quién es el dueño.
 */
public record UserOrderResponse(Long id, PlayerRefResponse player, OrderSide side, OwnerType ownerType,
                                OrderStatus status, int requestedQuantity, int executedQuantity,
                                int cancelledQuantity, int pendingQuantity, BigDecimal reservedCredits,
                                Instant createdAt) {

    public static UserOrderResponse from(MarketOrder order, Player player) {
        return new UserOrderResponse(order.getId(), PlayerRefResponse.from(player), order.getSide(),
                order.ownerType(), order.status(), order.getRequested(), order.getExecuted(), order.getCancelled(),
                order.pending(), order.getReservedCredits(), order.getCreatedAt());
    }
}
