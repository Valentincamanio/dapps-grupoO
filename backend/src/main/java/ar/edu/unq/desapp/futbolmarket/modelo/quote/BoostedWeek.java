package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Una semana de potenciados como se muestra: cada jugador con su porcentaje, en el orden en que se
 * eligieron, y en qué momento está la semana.
 */
public record BoostedWeek(QuoteWeek week, BoostWeekStatus status, List<BoostedPlayer> players) {

    public record BoostedPlayer(Player player, int percent) {
    }

    /**
     * Arma la semana con los jugadores ya leídos. Uno que ya no está en el catálogo no se muestra.
     */
    public static BoostedWeek of(WeekBoosts boosts, Map<Long, Player> players, Instant now) {
        var shown = boosts.boosts().stream()
                .filter(boost -> players.containsKey(boost.playerId()))
                .map(boost -> new BoostedPlayer(players.get(boost.playerId()), boost.percent()))
                .toList();
        return new BoostedWeek(boosts.week(), boosts.statusAt(now), shown);
    }
}
