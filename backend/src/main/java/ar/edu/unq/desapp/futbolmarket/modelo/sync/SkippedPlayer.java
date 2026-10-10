package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Jugador nuevo que no entró al catálogo por falta de un dato (FR-012). {@code name} puede faltar.
 */
public record SkippedPlayer(String externalId, String name, String teamName, PlayerSkipReason reason) {
}
