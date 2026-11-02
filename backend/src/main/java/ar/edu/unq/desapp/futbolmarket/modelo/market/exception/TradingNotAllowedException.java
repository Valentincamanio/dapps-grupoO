package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ForbiddenException;

/**
 * Solo los usuarios comunes operan: el superusuario vende únicamente por sus órdenes iniciales
 * (FR-010). Responde 403.
 */
public class TradingNotAllowedException extends ForbiddenException {

    private static final String MESSAGE = "El administrador no opera en el mercado.";

    public TradingNotAllowedException() {
        super(MESSAGE);
    }
}
