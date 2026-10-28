package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Otro pedido tiene bloqueado el libro del jugador o una de las cuentas más tiempo del que se
 * espera (research D13). Responde 409: el pedido no se hizo y se puede repetir.
 */
public class MarketBusyException extends ConflictException {

    private static final String MESSAGE = "El mercado está ocupado. Vuelva a intentar en unos segundos.";

    public MarketBusyException() {
        super(MESSAGE);
    }
}
