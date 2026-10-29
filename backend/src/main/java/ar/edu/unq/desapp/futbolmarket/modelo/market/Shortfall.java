package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * Lo que una compra pendiente no puede pagar cuando se cruza con una venta (FR-027 y research D11):
 * esos tokens se cancelan por saldo insuficiente y la venta sigue con la próxima compra. Es la
 * compra entera si no alcanza para un token.
 */
public record Shortfall(MarketOrder order, int quantity) {

    public Shortfall {
        if (order == null || order.getSide() != OrderSide.BUY || quantity < 1 || quantity > order.pending()) {
            throw new MarketInvariantException(
                    "Lo que no se puede pagar es de una compra, de al menos un token y no supera su pendiente.");
        }
    }
}
