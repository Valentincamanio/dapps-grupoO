package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * El estado del snapshot al empezar la corrida: leído, inexistente o ilegible (FR-027 y FR-028).
 */
public enum SnapshotStatus {
    LOADED,
    MISSING,
    UNREADABLE
}
