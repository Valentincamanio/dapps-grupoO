package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * Cómo quedó una parte después de un hecho: la foto de antes y la de después (FR-050).
 */
public record PartyChange(PartyState before, PartyState after) {

    public PartyChange {
        if (before == null || after == null || before.userId() != after.userId()) {
            throw new MarketInvariantException("Las dos fotos de una parte tienen que ser del mismo usuario.");
        }
    }

    public long userId() {
        return before.userId();
    }
}
