package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Un jugador de WhoScored que entró a la cancha y no se pudo cruzar: su nombre, el nombre del
 * equipo del catálogo de su lado y el partido (FR-021).
 */
public record UncrossedPlayer(String whoscoredId, String name, String teamName, String matchExternalId,
                              CrossFailure reason) {
}
