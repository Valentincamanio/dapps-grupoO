package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFormat;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Nadie vende tokens de un jugador mientras el superusuario todavía tenga (FR-011). Responde 409 e
 * informa cuántos le quedan.
 */
public class SaleBlockedByInitialOrderException extends ConflictException {
    public SaleBlockedByInitialOrderException(int remainingTokens) {
        super("Todavía no se puede vender: al administrador le " + (remainingTokens == 1 ? "queda " : "quedan ")
                + MarketFormat.tokens(remainingTokens) + " de este jugador.");
    }
}
