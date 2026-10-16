package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Por qué un equipo o un jugador no se pudo cruzar con WhoScored. {@code NOT_CONFIRMED} es, para un
 * equipo, que el nombre coincide pero el calendario no (FR-018), y para un jugador, que tenía un
 * único candidato en otro equipo por nombre y edad y la fecha de su página no lo confirmó (FR-019a).
 */
public enum CrossFailure {
    NOT_FOUND,
    AMBIGUOUS,
    NOT_CONFIRMED
}
