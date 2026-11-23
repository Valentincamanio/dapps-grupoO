package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

/**
 * La operación de una orden del mercado: la etiqueta {@code operation} de
 * {@code futbolmarket.market.orders}.
 */
public enum OrderOperation {
    BUY,
    SELL,
    CANCEL;

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
