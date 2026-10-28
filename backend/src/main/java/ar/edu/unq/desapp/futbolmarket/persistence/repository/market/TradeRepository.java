package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.TradeMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.TradeReferences;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.TradeSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.QuoteSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Guarda y lee operaciones. A propósito no tiene ningún método de actualización ni de borrado: una
 * operación guardada no cambia ni se pierde (FR-028 y SC-011), como las cotizaciones.
 */
@Repository
@RequiredArgsConstructor
public class TradeRepository {
    private final TradeSQLDAO tradeDAO;
    private final PlayerSQLDAO playerDAO;
    private final MarketOrderSQLDAO orderDAO;
    private final AppUserSQLDAO appUserDAO;
    private final QuoteSQLDAO quoteDAO;
    private final TradeMapper mapper;

    /**
     * Inserta las operaciones y las devuelve con su id. Las filas a las que apuntan se referencian
     * sin cargarlas.
     */
    @Transactional
    public List<Trade> saveAll(Collection<Trade> trades) {
        List<TradeSQL> rows = trades.stream().map(trade -> mapper.toSQL(trade, referencesOf(trade))).toList();
        return tradeDAO.saveAll(rows).stream().map(mapper::toDomain).toList();
    }

    public Optional<Trade> findById(long tradeId) {
        return tradeDAO.findById(tradeId).map(mapper::toDomain);
    }

    private TradeReferences referencesOf(Trade trade) {
        return new TradeReferences(
                playerDAO.getReferenceById(trade.playerId()),
                orderDAO.getReferenceById(trade.buyOrderId()),
                orderDAO.getReferenceById(trade.sellOrderId()),
                appUserDAO.getReferenceById(trade.buyerId()),
                appUserDAO.getReferenceById(trade.sellerId()),
                quoteDAO.getReferenceById(trade.quoteId()));
    }
}
