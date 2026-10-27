package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Lo que pasó con una liga en una corrida de WhoScored (FR-048): los partidos procesados desde el
 * snapshot y por scraping, los omitidos con su motivo, los equipos y jugadores sin cruzar, y las
 * entradas del snapshot salteadas por motivo.
 */
public record LeagueStatsResult(League league, LeagueStatsStatus status, int matchesFromSnapshot, int matchesScraped,
                                List<SkippedStatsMatch> skippedMatches, List<UncrossedTeam> uncrossedTeams,
                                List<UncrossedPlayer> uncrossedPlayers,
                                Map<SnapshotSkipReason, Integer> skippedSnapshotEntries) {
    public LeagueStatsResult {
        skippedMatches = List.copyOf(skippedMatches);
        uncrossedTeams = List.copyOf(uncrossedTeams);
        uncrossedPlayers = List.copyOf(uncrossedPlayers);
        Map<SnapshotSkipReason, Integer> skipped = new EnumMap<>(SnapshotSkipReason.class);
        skipped.putAll(skippedSnapshotEntries);
        skippedSnapshotEntries = Collections.unmodifiableMap(skipped);
    }

    /**
     * Una liga a la que el scraping no llegó porque la corrida se cortó antes (FR-044), con lo que
     * haya cargado del snapshot.
     */
    public static LeagueStatsResult notReached(League league, int matchesFromSnapshot,
                                               Map<SnapshotSkipReason, Integer> skipped) {
        return new LeagueStatsResult(league, LeagueStatsStatus.NOT_REACHED, matchesFromSnapshot, 0, List.of(),
                List.of(), List.of(), skipped);
    }
}
