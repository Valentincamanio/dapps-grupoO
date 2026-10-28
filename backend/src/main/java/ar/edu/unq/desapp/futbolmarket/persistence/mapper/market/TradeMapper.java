package ar.edu.unq.desapp.futbolmarket.persistence.mapper.market;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;

/**
 * Traduce una operación campo a campo. De las filas a las que apunta (jugador, órdenes, partes y
 * cotización) solo toma el id, sin cargarlas; al guardar, el repository le pasa las referencias en
 * un {@link TradeReferences}.
 */
@Component
public class TradeMapper {

    public Trade toDomain(TradeSQL row) {
        return new Trade(
                row.getId(),
                row.getExecutedAt(),
                row.getPlayer().getId(),
                row.getBuyOrder().getId(),
                row.getSellOrder().getId(),
                row.getBuyer().getId(),
                row.getSeller().getId(),
                OwnerType.valueOf(row.getSellerType()),
                row.getQuantity(),
                row.getTokenPrice(),
                row.getTotal(),
                row.getQuote().getId(),
                row.getBuyerBalanceBefore(),
                row.getBuyerBalanceAfter(),
                row.getSellerBalanceBefore(),
                row.getSellerBalanceAfter(),
                row.getBuyerTokensBefore(),
                row.getBuyerTokensAfter(),
                row.getSellerTokensBefore(),
                row.getSellerTokensAfter(),
                row.getRealizedProfit());
    }

    public TradeSQL toSQL(Trade trade, TradeReferences references) {
        return new TradeSQL(
                trade.id(),
                trade.executedAt(),
                references.player(),
                references.buyOrder(),
                references.sellOrder(),
                references.buyer(),
                references.seller(),
                trade.sellerType().name(),
                trade.quantity(),
                trade.tokenPrice(),
                trade.total(),
                references.quote(),
                trade.buyerBalanceBefore(),
                trade.buyerBalanceAfter(),
                trade.sellerBalanceBefore(),
                trade.sellerBalanceAfter(),
                trade.buyerTokensBefore(),
                trade.buyerTokensAfter(),
                trade.sellerTokensBefore(),
                trade.sellerTokensAfter(),
                trade.realizedProfit());
    }
}
