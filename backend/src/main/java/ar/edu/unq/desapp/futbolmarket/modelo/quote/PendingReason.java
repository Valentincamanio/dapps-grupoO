package ar.edu.unq.desapp.futbolmarket.modelo.quote;

/**
 * Por qué una semana cerrada todavía no se cotiza (research D11): le faltan estadísticas de
 * partidos terminados, o una semana anterior está pendiente y las semanas se cotizan en orden.
 */
public enum PendingReason {
    MISSING_STATS,
    PREVIOUS_WEEK_PENDING
}
