package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

/**
 * Cómo terminó una sincronización: sin ligas fallidas ni corte, con alguna falla, o rechazada porque
 * ya había otra en curso. La etiqueta {@code result} de {@code futbolmarket.sync}.
 */
public enum SyncResult {
    SUCCESS,
    FAILURE,
    IN_PROGRESS;

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
