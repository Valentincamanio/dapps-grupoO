package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Por qué una sincronización no inactivó a nadie: solo una completa con las cinco ligas en éxito
 * puede saber quién dejó las ligas (FR-017 y FR-018).
 */
public enum InactivationSkipReason {
    SINGLE_LEAGUE,
    FAILED_LEAGUES
}
