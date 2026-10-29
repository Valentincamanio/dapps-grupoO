package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decide qué se cruza en un pedido, sin cambiar nada (research D10): es puro y sin estado. Aplicar
 * el plan sobre créditos, tokens y órdenes es tarea de {@link PlayerBook}.
 *
 * <p>El orden de las contrapartes es total ({@code createdAt} y después {@code id}), así el
 * resultado no depende del orden en que llegan las listas. Las órdenes del mismo dueño nunca se
 * cruzan (FR-017).</p>
 */
public final class OrderMatcher {
    private static final Comparator<MarketOrder> OLDEST_FIRST =
            Comparator.comparing(MarketOrder::getCreatedAt).thenComparing(MarketOrder::getId);

    private OrderMatcher() {
    }

    /**
     * El lado de compra (FR-022): primero la orden inicial a {@link MarketMath#INITIAL_TOKEN_PRICE}
     * hasta agotarla, después las ventas pendientes de otros usuarios de la más vieja a la más
     * nueva, al precio vigente. Lo que no alcanza queda como remanente.
     *
     * @param initialOrder la orden inicial del jugador, que puede estar agotada
     * @param opposite     las órdenes abiertas del jugador, en cualquier orden: se usan solo las ventas
     * @param tokenPrice   el precio por token vigente
     */
    public static MatchPlan matchBuy(long ownerId, int quantity, Optional<MarketOrder> initialOrder,
                                     Collection<MarketOrder> opposite, BigDecimal tokenPrice) {
        List<Fill> fills = new ArrayList<>();
        int remaining = quantity;
        if (initialOrder.isPresent() && initialOrder.get().pending() > 0) {
            int fromInitial = Math.min(remaining, initialOrder.get().pending());
            fills.add(Fill.of(initialOrder.get(), fromInitial, MarketMath.INITIAL_TOKEN_PRICE));
            remaining -= fromInitial;
        }
        for (MarketOrder sell : sellsOfOthers(ownerId, opposite)) {
            if (remaining == 0) {
                break;
            }
            int fromSell = Math.min(remaining, sell.pending());
            fills.add(Fill.of(sell, fromSell, tokenPrice));
            remaining -= fromSell;
        }
        return new MatchPlan(fills, remaining);
    }

    /**
     * El lado de venta (FR-023, FR-027 y research D11): las compras pendientes de otros usuarios, de
     * la más vieja a la más nueva, al precio vigente. A cada compra se le ejecuta lo que su dueño
     * puede pagar: su reserva más su libre, nunca la reserva de otras órdenes. Si alcanza para
     * menos que su pendiente y que lo que falta vender, el resto de esa compra es un faltante (la
     * compra entera si no alcanza para un token) y la venta sigue con la próxima.
     *
     * <p>Si un dueño tiene más de una compra, lo que una toma de su libre ya no está para la
     * siguiente.</p>
     *
     * @param opposite     las órdenes abiertas del jugador, en cualquier orden: se usan solo las compras
     * @param freeBalances el saldo libre de cada dueño de esas compras, por id
     * @param tokenPrice   el precio por token vigente
     */
    public static MatchPlan matchSell(long ownerId, int quantity, Collection<MarketOrder> opposite,
                                      Map<Long, BigDecimal> freeBalances, BigDecimal tokenPrice) {
        List<Fill> fills = new ArrayList<>();
        List<Shortfall> shortfalls = new ArrayList<>();
        Map<Long, BigDecimal> free = new HashMap<>(freeBalances);
        int remaining = quantity;
        for (MarketOrder purchase : buysOfOthers(ownerId, opposite)) {
            if (remaining == 0) {
                break;
            }
            BigDecimal ownerFree = free.getOrDefault(purchase.getOwnerId(), MarketMath.ZERO_CREDITS);
            int wanted = Math.min(remaining, purchase.pending());
            int affordable = MarketMath.maxAffordable(purchase.getReservedCredits().add(ownerFree), tokenPrice, wanted);
            if (affordable > 0) {
                Fill fill = Fill.of(purchase, affordable, tokenPrice);
                fills.add(fill);
                BigDecimal fromFree = fill.total().subtract(purchase.getReservedCredits()).max(MarketMath.ZERO_CREDITS);
                free.put(purchase.getOwnerId(), ownerFree.subtract(fromFree));
                remaining -= affordable;
            }
            if (affordable < wanted) {
                shortfalls.add(new Shortfall(purchase, purchase.pending() - affordable));
            }
        }
        return new MatchPlan(fills, remaining, shortfalls);
    }

    private static List<MarketOrder> sellsOfOthers(long ownerId, Collection<MarketOrder> orders) {
        return openOfOthers(ownerId, OrderSide.SELL, orders);
    }

    private static List<MarketOrder> buysOfOthers(long ownerId, Collection<MarketOrder> orders) {
        return openOfOthers(ownerId, OrderSide.BUY, orders);
    }

    private static List<MarketOrder> openOfOthers(long ownerId, OrderSide side, Collection<MarketOrder> orders) {
        return orders.stream()
                .filter(order -> order.getSide() == side && !order.isInitial() && order.isOpen())
                .filter(order -> order.getOwnerId() != ownerId)
                .sorted(OLDEST_FIRST)
                .toList();
    }
}
