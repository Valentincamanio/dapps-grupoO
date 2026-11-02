package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ForbiddenException;

/**
 * La orden es de otro usuario, o es una orden inicial del superusuario (research D16). Responde
 * 403: las órdenes son públicas en el listado, así que no hay nada que ocultar con un 404.
 */
public class OrderNotOwnedException extends ForbiddenException {

    private static final String MESSAGE = "La orden no pertenece al usuario.";

    public OrderNotOwnedException() {
        super(MESSAGE);
    }
}
