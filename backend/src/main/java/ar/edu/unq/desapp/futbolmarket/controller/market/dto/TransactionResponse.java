package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;

/**
 * Una operación del historial vista por una de sus partes (esquema {@code Transaction}): su lado, la
 * contraparte por su tipo y nunca por quién es, su propia orden y la cotización usada. La ganancia
 * realizada solo va en las ventas del usuario.
 */
public record TransactionResponse(Long id, Instant executedAt, PlayerRefResponse player, OrderSide side, int quantity,
                                  BigDecimal tokenPrice, BigDecimal total, OwnerType counterparty, Long orderId,
                                  QuoteUsedResponse quote,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal realizedProfit) {

    public static TransactionResponse from(TradeView view, long userId) {
        Trade trade = view.trade();
        OrderSide side = trade.sideFor(userId);
        boolean sale = side == OrderSide.SELL;
        return new TransactionResponse(trade.id(), trade.executedAt(), PlayerRefResponse.from(view.player()), side,
                trade.quantity(), trade.tokenPrice(), trade.total(), trade.counterpartyFor(userId),
                sale ? trade.sellOrderId() : trade.buyOrderId(), QuoteUsedResponse.from(view.quote()),
                sale ? trade.realizedProfit() : null);
    }
}
