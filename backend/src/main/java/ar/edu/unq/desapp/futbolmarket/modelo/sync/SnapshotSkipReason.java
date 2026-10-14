package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Por qué una entrada del snapshot no se cargó (FR-026; research D20).
 */
public enum SnapshotSkipReason {
    TEAM_NOT_IN_CATALOG,
    MATCH_NOT_IN_CATALOG,
    PLAYER_NOT_IN_CATALOG,
    MATCH_NOT_FINISHED,
    CONFLICTING_CROSS
}
