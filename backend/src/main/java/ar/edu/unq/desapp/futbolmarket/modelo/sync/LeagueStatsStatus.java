package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Cómo terminó una liga en una corrida de WhoScored: procesada, cortada por un bloqueo o sin
 * alcanzar porque la corrida se cortó antes (FR-044).
 */
public enum LeagueStatsStatus {
    PROCESSED,
    CUT,
    NOT_REACHED
}
