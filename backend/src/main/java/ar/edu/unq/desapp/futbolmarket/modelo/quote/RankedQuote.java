package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Una fila del ranking: un jugador activo, con su equipo, y su última cotización.
 */
public record RankedQuote(Player player, Quote quote) {
}
