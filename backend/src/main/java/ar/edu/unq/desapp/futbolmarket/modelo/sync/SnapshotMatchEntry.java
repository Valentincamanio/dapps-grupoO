package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Entrada {@code match} del snapshot: el partido de Football-Data {@code matchExternalId} es el
 * partido {@code whoscoredId} de WhoScored. Incluye partidos todavía sin estadísticas, también
 * futuros. La liga es la de la temporada del partido.
 */
public record SnapshotMatchEntry(League league, String matchExternalId, String whoscoredId) {
}
