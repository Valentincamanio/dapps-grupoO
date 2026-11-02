package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * Una operación ejecutada: tokens que pasan del vendedor al comprador a un precio, con la
 * cotización vigente y el saldo total y los tokens de cada parte antes y después (FR-028).
 *
 * <p>Es inmutable y no tiene operaciones de cambio: una operación guardada no se modifica ni se
 * borra (SC-011). Los tokens del superusuario son el pendiente de su orden inicial, y como no
 * compró, sus ventas no tienen ganancia realizada (research D7 y D12).</p>
 */
public record Trade(Long id, Instant executedAt, long playerId, Long buyOrderId, Long sellOrderId,
                    long buyerId, long sellerId, OwnerType sellerType, int quantity, BigDecimal tokenPrice,
                    BigDecimal total, long quoteId,
                    BigDecimal buyerBalanceBefore, BigDecimal buyerBalanceAfter,
                    BigDecimal sellerBalanceBefore, BigDecimal sellerBalanceAfter,
                    int buyerTokensBefore, int buyerTokensAfter, int sellerTokensBefore, int sellerTokensAfter,
                    BigDecimal realizedProfit) {

    public Trade {
        if (executedAt == null || sellerType == null || tokenPrice == null || total == null) {
            throw new MarketInvariantException("Una operación necesita su instante, el tipo de vendedor, el precio y el total.");
        }
        if (buyerId == sellerId) {
            throw new MarketInvariantException("Un usuario no opera consigo mismo.");
        }
        if (quantity < 1) {
            throw new MarketInvariantException("Una operación tiene al menos un token: " + quantity + ".");
        }
        if (total.compareTo(MarketMath.total(quantity, tokenPrice)) != 0) {
            throw new MarketInvariantException("El total de " + quantity + " tokens a " + tokenPrice + " no es " + total + ".");
        }
        if ((sellerType == OwnerType.ADMIN) != (realizedProfit == null)) {
            throw new MarketInvariantException("Solo las ventas del superusuario no tienen ganancia realizada.");
        }
    }

    /**
     * Una copia con los ids de las dos órdenes, que se conocen recién al guardarlas: el cruce arma
     * la operación antes, con la orden del pedido todavía sin id (data-model 2.10). La operación
     * original no cambia.
     */
    public Trade withOrderIds(Long buyOrder, Long sellOrder) {
        return new Trade(id, executedAt, playerId, buyOrder, sellOrder, buyerId, sellerId, sellerType, quantity,
                tokenPrice, total, quoteId, buyerBalanceBefore, buyerBalanceAfter, sellerBalanceBefore,
                sellerBalanceAfter, buyerTokensBefore, buyerTokensAfter, sellerTokensBefore, sellerTokensAfter,
                realizedProfit);
    }

    /**
     * El lado del usuario en la operación: compra o venta.
     */
    public OrderSide sideFor(long userId) {
        if (userId == buyerId) {
            return OrderSide.BUY;
        }
        if (userId == sellerId) {
            return OrderSide.SELL;
        }
        throw new MarketInvariantException("El usuario " + userId + " no es parte de la operación.");
    }

    /**
     * Contra quién operó el usuario, sin decir quién es: el administrador (su orden inicial) u otro
     * usuario. El comprador siempre es un usuario.
     */
    public OwnerType counterpartyFor(long userId) {
        return sideFor(userId) == OrderSide.BUY ? sellerType : OwnerType.USER;
    }
}
