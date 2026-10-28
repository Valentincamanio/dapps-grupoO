package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFormat;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * El saldo libre no alcanza para lo que el pedido necesita (FR-015). Responde 409.
 */
public class InsufficientCreditsException extends ConflictException {
    public InsufficientCreditsException(BigDecimal needed, BigDecimal available) {
        super("Saldo insuficiente: se necesitan " + MarketFormat.credits(needed)
                + " créditos y el saldo libre es " + MarketFormat.credits(available) + ".");
    }
}
