package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * El jugador de una orden o una operación, con los campos del esquema {@code PlayerRef}.
 */
public record PlayerRefResponse(Long id, String name, String team, League league) {

    public static PlayerRefResponse from(Player player) {
        return new PlayerRefResponse(player.id(), player.name(), player.team().name(), player.league());
    }
}
