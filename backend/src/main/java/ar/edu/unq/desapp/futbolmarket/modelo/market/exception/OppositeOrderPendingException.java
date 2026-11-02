package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Un usuario no compra y vende el mismo jugador a la vez (FR-017): se rechaza el pedido si tiene
 * una orden pendiente del otro lado. Responde 409.
 */
public class OppositeOrderPendingException extends ConflictException {
    public OppositeOrderPendingException(OrderSide pendingSide) {
        super("Ya tiene una orden de " + (pendingSide == OrderSide.BUY ? "compra" : "venta")
                + " pendiente de este jugador.");
    }
}
