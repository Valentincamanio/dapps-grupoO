package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketMath;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.TradeMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.TradeReferences;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.QuoteMapper;
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
    private final PlayerMapper playerMapper;
    private final QuoteMapper quoteMapper;

    /**
     * Una hoja del historial del usuario, de la más nueva a la más vieja, cada operación con su
     * jugador y la cotización usada, en una sola consulta (research D21).
     */
    @Transactional(readOnly = true)
    public QuotePage<TradeView> findHistory(long userId, TradeFilter filter, int page, int size) {
        String side = filter.side() == null ? null : filter.side().name();
        Page<TradeSQL> rows = tradeDAO.findHistory(userId, side, filter.playerId(), filter.fromInstant(),
                filter.toExclusive(), PageRequest.of(page, size));
        List<TradeView> views = rows.getContent().stream()
                .map(row -> new TradeView(mapper.toDomain(row), playerMapper.toDomain(row.getPlayer()),
                        quoteMapper.toDomain(row.getQuote())))
                .toList();
        return new QuotePage<>(views, rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

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

    /**
     * La ganancia realizada acumulada en las ventas del usuario: 0 si nunca vendió.
     */
    @Transactional(readOnly = true)
    public BigDecimal realizedProfitOf(long userId) {
        BigDecimal realized = tradeDAO.sumRealizedProfitOfSeller(userId);
        return realized == null ? MarketMath.ZERO_CREDITS : realized.setScale(MarketMath.CREDIT_SCALE);
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
