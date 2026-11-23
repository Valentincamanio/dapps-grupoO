package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

/**
 * Cómo terminó un recálculo de cotizaciones: completo, interrumpido o abortado, o rechazado por el
 * candado (otro recálculo o una sincronización en curso). La etiqueta {@code result} de
 * {@code futbolmarket.quotes.recalculation}.
 */
public enum RecalculationResult {
    SUCCESS,
    FAILURE,
    BLOCKED;

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
