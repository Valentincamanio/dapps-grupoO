package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Una aparición ignorada de un jugador que la fuente informa en más de un plantel (FR-015).
 */
public record DuplicatedPlayer(String externalId, String name, String keptTeamName, String ignoredTeamName) {
}
