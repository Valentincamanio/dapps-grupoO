package ar.edu.unq.desapp.futbolmarket.persistence.mapper.market;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.MarketOrderSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;

/**
 * Las filas a las que apunta una operación, como referencias que el repository obtiene sin
 * cargarlas.
 */
public record TradeReferences(PlayerSQL player, MarketOrderSQL buyOrder, MarketOrderSQL sellOrder,
                              AppUserSQL buyer, AppUserSQL seller, QuoteSQL quote) {
}
