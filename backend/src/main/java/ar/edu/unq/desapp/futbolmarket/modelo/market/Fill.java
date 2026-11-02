package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * Un cruce del plan: cuántos tokens de la orden entrante se ejecutan contra {@code counterpart}, a
 * qué precio por token y por qué total (research D10). El total sale de {@link MarketMath#total}:
 * el comprador paga y el vendedor cobra exactamente eso.
 */
public record Fill(MarketOrder counterpart, int quantity, BigDecimal tokenPrice, BigDecimal total) {

    public Fill {
        if (counterpart == null || quantity < 1 || quantity > counterpart.pending()) {
            throw new MarketInvariantException("Un cruce es de al menos un token y no supera el pendiente de la contraparte.");
        }
    }

    public static Fill of(MarketOrder counterpart, int quantity, BigDecimal tokenPrice) {
        return new Fill(counterpart, quantity, tokenPrice, MarketMath.total(quantity, tokenPrice));
    }
}
