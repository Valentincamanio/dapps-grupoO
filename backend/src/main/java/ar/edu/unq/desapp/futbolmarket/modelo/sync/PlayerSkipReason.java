package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Dato obligatorio que le falta a un jugador nuevo para entrar al catálogo. El jugador se
 * saltea y figura en el informe con este motivo (FR-012).
 */
public enum PlayerSkipReason {
    MISSING_NAME,
    MISSING_POSITION
}
