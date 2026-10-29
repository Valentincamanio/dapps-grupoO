package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * Una operación con lo que muestra el historial (FR-044): el jugador con su equipo y su liga, y la
 * cotización usada con su vigencia, su score y la estrategia y versión que la calcularon. Es un
 * modelo de lectura, como {@link OrderView}: no se guarda.
 */
public record TradeView(Trade trade, Player player, Quote quote) {
}
