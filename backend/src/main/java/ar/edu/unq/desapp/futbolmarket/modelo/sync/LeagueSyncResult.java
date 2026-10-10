package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;

/**
 * Lo que pasó con una liga en una sincronización. Una liga fallida no trae temporada, conteos ni
 * listas: no se escribió nada de ella (FR-034).
 */
public record LeagueSyncResult(League league, LeagueSyncStatus status, String failureReason, Season season,
                               EntityCounts teams, EntityCounts players, EntityCounts matches,
                               List<SkippedPlayer> skippedPlayers, List<SkippedMatch> skippedMatches,
                               List<Player> reactivatedPlayers) {
    public LeagueSyncResult {
        skippedPlayers = List.copyOf(skippedPlayers);
        skippedMatches = List.copyOf(skippedMatches);
        reactivatedPlayers = List.copyOf(reactivatedPlayers);
    }

    public static LeagueSyncResult failed(League league, String reason) {
        return new LeagueSyncResult(league, LeagueSyncStatus.FAILED, reason, null, EntityCounts.none(),
                EntityCounts.none(), EntityCounts.none(), List.of(), List.of(), List.of());
    }

    public boolean succeeded() {
        return status == LeagueSyncStatus.SUCCEEDED;
    }
}
