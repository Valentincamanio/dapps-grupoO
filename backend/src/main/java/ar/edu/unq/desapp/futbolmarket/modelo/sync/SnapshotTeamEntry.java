package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Entrada {@code team} del snapshot: el equipo de Football-Data {@code teamExternalId} es el equipo
 * {@code whoscoredId} de WhoScored. La liga es la actual del equipo en el catálogo.
 */
public record SnapshotTeamEntry(League league, String teamExternalId, String whoscoredId) {
}
