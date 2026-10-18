package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Entrada {@code stats} del snapshot: el partido ya tiene estadísticas, con una fila por jugador
 * cruzado que entró a la cancha. Puede no tener filas: es un partido procesado en el que no se
 * cruzó a nadie.
 */
public record SnapshotStatsEntry(League league, String matchExternalId, List<SnapshotPlayerRow> players) {
    public SnapshotStatsEntry {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
