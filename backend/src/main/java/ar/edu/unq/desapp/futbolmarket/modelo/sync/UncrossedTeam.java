package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Un equipo del catálogo que no se pudo cruzar con WhoScored, con su motivo.
 */
public record UncrossedTeam(String teamExternalId, String name, CrossFailure reason) {
}
