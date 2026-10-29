package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Las posiciones de una cuenta a una fecha, reconstruidas con sus operaciones (data-model 2.12,
 * research D20). Es dominio puro: recibe las operaciones ya filtradas hasta el final del día y las
 * reproduce en orden ({@code executedAt}, después {@code id}). Los jugadores que quedaron en 0 no
 * figuran.
 *
 * <p>Las de un usuario llevan su costo, el mismo {@link CostBasis} de la tenencia actual (research
 * D12), y la ganancia realizada de sus ventas. Las del superusuario solo cuentan tokens: no compró,
 * así que no tienen costo ni ganancia realizada (research D7).</p>
 */
public final class TradeReplay {

    private static final Comparator<Trade> IN_ORDER = Comparator.comparing(Trade::executedAt)
            .thenComparing(Trade::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Map<Long, Integer> tokensByPlayer;
    private final Map<Long, CostBasis> basisByPlayer;
    private final BigDecimal realizedProfit;

    private TradeReplay(Map<Long, Integer> tokensByPlayer, Map<Long, CostBasis> basisByPlayer,
                        BigDecimal realizedProfit) {
        this.tokensByPlayer = Collections.unmodifiableMap(tokensByPlayer);
        this.basisByPlayer = Collections.unmodifiableMap(basisByPlayer);
        this.realizedProfit = realizedProfit;
    }

    /**
     * Las posiciones de un usuario: cada compra suma a su costo y cada venta lo resta al precio
     * promedio, acumulando la ganancia realizada (FR-037).
     */
    public static TradeReplay ofUser(long userId, List<Trade> trades) {
        Map<Long, CostBasis> bases = new TreeMap<>();
        BigDecimal realized = MarketMath.ZERO_CREDITS;
        for (Trade trade : inOrder(trades)) {
            CostBasis basis = bases.getOrDefault(trade.playerId(), CostBasis.EMPTY);
            if (trade.sideFor(userId) == OrderSide.BUY) {
                bases.put(trade.playerId(), basis.acquire(trade.quantity(), trade.total()));
            } else {
                Disposal disposal = basis.dispose(trade.quantity(), trade.total());
                bases.put(trade.playerId(), disposal.basis());
                realized = realized.add(disposal.realizedProfit());
            }
        }
        bases.values().removeIf(basis -> basis.quantity() == 0);
        Map<Long, Integer> tokens = new TreeMap<>();
        bases.forEach((playerId, basis) -> tokens.put(playerId, basis.quantity()));
        return new TradeReplay(tokens, bases, realized);
    }

    /**
     * Las posiciones del superusuario: los tokens de cada orden inicial creada hasta esa fecha (100
     * por jugador) menos los que vendió hasta entonces.
     *
     * @param initialOrders sus órdenes iniciales creadas antes del final del día
     * @param trades        sus operaciones anteriores al final del día: en todas vende
     */
    public static TradeReplay ofSuperuser(List<MarketOrder> initialOrders, List<Trade> trades) {
        Map<Long, Integer> tokens = new TreeMap<>();
        initialOrders.forEach(order -> tokens.merge(order.getPlayerId(), order.getRequested(), Integer::sum));
        trades.forEach(trade -> tokens.merge(trade.playerId(), -trade.quantity(), Integer::sum));
        tokens.values().removeIf(quantity -> quantity <= 0);
        return new TradeReplay(tokens, Map.of(), null);
    }

    /**
     * El saldo total de la cuenta al final del día (research D20). Solo cambia con operaciones
     * (research D5), así que es el posterior de su última operación anterior al final del día; si no
     * tiene ninguna anterior, el previo de la primera posterior; si no operó nunca, el actual.
     *
     * @param lastBefore     su última operación anterior al final del día; nula si no tiene
     * @param firstAtOrAfter su primera operación desde el final del día; nula si no tiene
     */
    public static BigDecimal balanceAt(long userId, Trade lastBefore, Trade firstAtOrAfter,
                                       BigDecimal currentBalance) {
        if (lastBefore != null) {
            return lastBefore.sideFor(userId) == OrderSide.BUY ? lastBefore.buyerBalanceAfter()
                    : lastBefore.sellerBalanceAfter();
        }
        if (firstAtOrAfter != null) {
            return firstAtOrAfter.sideFor(userId) == OrderSide.BUY ? firstAtOrAfter.buyerBalanceBefore()
                    : firstAtOrAfter.sellerBalanceBefore();
        }
        return currentBalance;
    }

    /**
     * Los jugadores con tokens a esa fecha, de menor a mayor id.
     */
    public Set<Long> playerIds() {
        return tokensByPlayer.keySet();
    }

    public int tokensOf(long playerId) {
        return tokensByPlayer.getOrDefault(playerId, 0);
    }

    /**
     * Lo que costaron los tokens de ese jugador; vacío para el superusuario, que no compró.
     */
    public Optional<CostBasis> basisOf(long playerId) {
        return Optional.ofNullable(basisByPlayer.get(playerId));
    }

    /**
     * Si las posiciones llevan costo: las de un usuario sí, las del superusuario no.
     */
    public boolean withCost() {
        return realizedProfit != null;
    }

    /**
     * La ganancia realizada en las ventas hasta esa fecha; nula para el superusuario.
     */
    public BigDecimal realizedProfit() {
        return realizedProfit;
    }

    private static List<Trade> inOrder(List<Trade> trades) {
        return trades.stream().sorted(IN_ORDER).toList();
    }
}
