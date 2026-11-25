package ar.edu.unq.desapp.futbolmarket.observability;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;

/**
 * De dónde vino la venta de una operación ejecutada (research R8 de la 013): la orden inicial del
 * superusuario o un usuario. Es la etiqueta {@code counterparty} de
 * {@code futbolmarket.market.trades}, {@code .tokens.traded} y {@code .credits.traded}, con valores
 * cerrados (FR-020).
 */
public enum TradeCounterparty {
    INITIAL_ORDER("initial_order"),
    USER("user");

    private final String tag;

    TradeCounterparty(String tag) {
        this.tag = tag;
    }

    /**
     * El valor de la etiqueta.
     */
    public String tag() {
        return tag;
    }

    /**
     * La contraparte según el dueño de la venta: solo el superusuario tiene órdenes {@code ADMIN},
     * y son sus órdenes iniciales.
     */
    public static TradeCounterparty from(OwnerType sellerType) {
        if (sellerType == null) {
            throw new IllegalArgumentException("Una operación necesita el tipo de vendedor para medirse.");
        }
        return sellerType == OwnerType.ADMIN ? INITIAL_ORDER : USER;
    }
}
