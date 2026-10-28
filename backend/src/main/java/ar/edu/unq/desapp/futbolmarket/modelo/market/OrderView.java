package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Una orden con su jugador, para mostrarla: el listado del mercado, las órdenes propias y las
 * iniciales del portfolio del superusuario. Es un modelo de lectura, como {@code TradeView}: no se
 * guarda, y la orden sigue referenciando al jugador solo por id.
 */
public record OrderView(MarketOrder order, Player player) {
}
