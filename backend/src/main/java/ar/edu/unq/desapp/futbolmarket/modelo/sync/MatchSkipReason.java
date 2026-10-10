package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Motivo por el que un partido no se guarda. Ninguno hace fallar la liga (FR-024).
 */
public enum MatchSkipReason {
    UNKNOWN_TEAM,
    UNKNOWN_STATUS
}
