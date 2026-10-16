package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Lo que se guarda de un partido en una transacción: el partido, que ya viene con
 * {@code markStatsLoaded()} y, si hacía falta, con su id de WhoScored; los jugadores cruzados en
 * este partido; y las estadísticas de los que entraron y se cruzaron.
 */
public record MatchStatsLoad(Match match, List<Player> crossedPlayers, List<PlayerMatchStats> stats,
                             LoadSource source) {
    public MatchStatsLoad {
        crossedPlayers = List.copyOf(crossedPlayers);
        stats = List.copyOf(stats);
    }
}
