package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFormat;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se pide vender más tokens que los libres: la tenencia menos los comprometidos en otras ventas
 * pendientes (FR-016). Responde 409.
 */
public class InsufficientTokensException extends ConflictException {
    public InsufficientTokensException(int freeTokens) {
        super("Tokens insuficientes: tiene " + MarketFormat.tokens(freeTokens)
                + (freeTokens == 1 ? " libre" : " libres") + " de este jugador.");
    }
}
