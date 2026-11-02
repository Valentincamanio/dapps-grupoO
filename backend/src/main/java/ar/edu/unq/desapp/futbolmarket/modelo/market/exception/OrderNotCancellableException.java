package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Solo se cancela una orden abierta (FR-035). Se lanza con el estado final de la orden,
 * {@code EXECUTED} o {@code CANCELLED}. Responde 409.
 */
public class OrderNotCancellableException extends ConflictException {
    public OrderNotCancellableException(OrderStatus status) {
        super("La orden ya está " + (status == OrderStatus.EXECUTED ? "ejecutada" : "cancelada") + ".");
    }
}
