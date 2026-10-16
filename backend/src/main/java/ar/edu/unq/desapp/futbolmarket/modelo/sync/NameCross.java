package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Un jugador de WhoScored cruzado por un nivel de nombre de FR-019b: el nombre que informó
 * WhoScored, el jugador del catálogo, ya con su id de WhoScored, y el nivel que lo resolvió. Va al
 * registro para poder revisar cada cruce (research D14).
 */
public record NameCross(String reportedName, Player player, NameMatchLevel level) {
}
