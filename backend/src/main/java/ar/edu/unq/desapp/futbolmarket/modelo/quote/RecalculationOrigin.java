package ar.edu.unq.desapp.futbolmarket.modelo.quote;

/**
 * Qué disparó el recálculo: el cron del lunes, el arranque de la aplicación, un administrador o un
 * cambio de la estrategia activa con recotización en el acto.
 */
public enum RecalculationOrigin {
    WEEKLY,
    STARTUP,
    MANUAL,
    STRATEGY_CHANGE
}
