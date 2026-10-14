package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Por qué un equipo o un jugador no se pudo cruzar con WhoScored. {@code NOT_CONFIRMED} es solo para
 * equipos: el nombre coincide pero el calendario no (FR-018).
 */
public enum CrossFailure {
    NOT_FOUND,
    AMBIGUOUS,
    NOT_CONFIRMED
}
