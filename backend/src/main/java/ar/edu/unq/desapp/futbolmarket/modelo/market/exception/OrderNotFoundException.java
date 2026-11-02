package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(Long orderId) {
        super("No se encontró la orden con id " + orderId + ".");
    }
}
