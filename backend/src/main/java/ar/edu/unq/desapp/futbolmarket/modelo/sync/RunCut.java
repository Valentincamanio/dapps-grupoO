package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Dónde se cortó una corrida de WhoScored: la página rechazada, la liga en curso y, si la página
 * era la de un partido, su id de Football-Data. Si no, {@code matchExternalId} es {@code null}.
 */
public record RunCut(BlockedPage page, League league, String matchExternalId) {
}
