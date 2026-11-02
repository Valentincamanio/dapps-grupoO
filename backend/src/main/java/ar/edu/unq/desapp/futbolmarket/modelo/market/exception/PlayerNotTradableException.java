package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.market.NotTradableReason;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * El jugador existe pero no se puede operar (research D15). Responde 409 con el motivo.
 */
public class PlayerNotTradableException extends ConflictException {
    public PlayerNotTradableException(NotTradableReason reason) {
        super("El jugador no se puede operar: " + describe(reason) + ".");
    }

    private static String describe(NotTradableReason reason) {
        return switch (reason) {
            case INACTIVE -> "está inactivo";
            case WITHOUT_QUOTE -> "todavía no tiene cotización";
            case WITHOUT_INITIAL_ORDER -> "todavía no tiene su orden inicial";
        };
    }
}
