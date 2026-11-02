package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Una tenencia con su jugador, para el portfolio actual. Es un modelo de lectura, como
 * {@link OrderView}: no se guarda, y la tenencia sigue referenciando al jugador solo por id.
 */
public record HoldingView(Holding holding, Player player) {
}
