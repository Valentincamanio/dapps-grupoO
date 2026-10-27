package ar.edu.unq.desapp.futbolmarket.modelo.performance;

/**
 * Las 15 métricas que se cuentan de un jugador en un partido (FR-002). El orden importa: es el de
 * las columnas de la tabla, el del mapper y el de las filas del snapshot.
 */
public enum Metric {
    GOALS,
    ASSISTS,
    SHOTS,
    SHOTS_ON_TARGET,
    KEY_PASSES,
    PASSES,
    ACCURATE_PASSES,
    SUCCESSFUL_DRIBBLES,
    TACKLES,
    INTERCEPTIONS,
    CLEARANCES,
    AERIALS_WON,
    FOULS_COMMITTED,
    YELLOW_CARDS,
    RED_CARDS
}
