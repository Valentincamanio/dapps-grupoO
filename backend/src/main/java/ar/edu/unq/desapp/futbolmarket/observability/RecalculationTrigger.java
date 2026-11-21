package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;

/**
 * Qué disparó un recálculo de cotizaciones: la etiqueta {@code trigger} de
 * {@code futbolmarket.quotes.recalculation}. Se deriva de {@link RecalculationOrigin}.
 */
public enum RecalculationTrigger {
    MANUAL,
    SCHEDULED,
    STARTUP;

    /**
     * El cron semanal es {@code SCHEDULED}. {@code STRATEGY_CHANGE} no pasa por el recálculo
     * instrumentado, pero lo dispara un administrador: si llegara, cuenta como {@code MANUAL}.
     */
    public static RecalculationTrigger from(RecalculationOrigin origin) {
        return switch (origin) {
            case MANUAL, STRATEGY_CHANGE -> MANUAL;
            case WEEKLY -> SCHEDULED;
            case STARTUP -> STARTUP;
        };
    }

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
