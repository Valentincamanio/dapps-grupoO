package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

/**
 * Cómo terminó un pedido de orden: aceptado por el mercado o rechazado por una regla del dominio
 * (409, 404 o 403). La etiqueta {@code result} de {@code futbolmarket.market.orders}.
 */
public enum OrderResult {
    SUCCESS,
    REJECTED;

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
