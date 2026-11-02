package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostedWeek;

/**
 * Un jugador potenciado: los datos que muestra el catálogo y su porcentaje (300 es ×3).
 * {@code teamCrest} sale en {@code null} si la fuente no lo informa.
 */
public record BoostedPlayerResponse(long playerId, String name, Position position, String team, League league,
                                    String teamCrest, int percent) {

    public static BoostedPlayerResponse from(BoostedWeek.BoostedPlayer boosted) {
        Player player = boosted.player();
        return new BoostedPlayerResponse(player.id(), player.name(), player.position(), player.team().name(),
                player.league(), player.team().crest(), boosted.percent());
    }
}
