package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OppositeOrderPendingException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.PlayerNotTradableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.SaleBlockedByInitialOrderException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * El libro de un jugador durante un pedido (research D10): valida el pedido, le pide el plan a
 * {@link OrderMatcher} y lo aplica sobre créditos, tokens y órdenes. Devuelve un
 * {@link BookOutcome} con todo lo que cambió.
 *
 * <p>Se arma con lo que el servicio cargó después de bloquear (research D13): el jugador, la
 * cotización vigente al instante del pedido, la orden inicial, las órdenes abiertas de usuarios
 * (de los dos lados), las cuentas bloqueadas y las tenencias del jugador de esas cuentas. El
 * superusuario no tiene tenencia: sus tokens son el pendiente de su orden inicial (research D7).</p>
 */
public final class PlayerBook {
    private final Player player;
    private final Quote quote;
    private final MarketOrder initialOrder;
    private final List<MarketOrder> openOrders;
    private final Map<Long, AppUser> accounts;
    private final Map<Long, Holding> holdings = new HashMap<>();

    public PlayerBook(Player player, Optional<Quote> quote, Optional<MarketOrder> initialOrder,
                      List<MarketOrder> openOrders, Map<Long, AppUser> accounts, Collection<Holding> holdings) {
        this.player = player;
        this.quote = quote.orElse(null);
        this.initialOrder = initialOrder.orElse(null);
        this.openOrders = List.copyOf(openOrders);
        this.accounts = Map.copyOf(accounts);
        holdings.forEach(holding -> this.holdings.put(holding.getOwnerId(), holding));
    }

    /**
     * Las cuentas que un pedido tiene que bloquear (research D13): la de quien pide, la del
     * superusuario si es una compra y su orden inicial tiene pendiente, y las de los dueños de todas
     * las órdenes abiertas del otro lado, porque cuáles se usan depende del cruce. Ordenadas de
     * menor a mayor id.
     */
    public static Set<Long> accountsToLock(long requesterId, OrderSide side, Optional<MarketOrder> initialOrder,
                                           Collection<MarketOrder> openOrders) {
        Set<Long> ids = new TreeSet<>();
        ids.add(requesterId);
        initialOrder.filter(initial -> side == OrderSide.BUY && initial.pending() > 0)
                .ifPresent(initial -> ids.add(initial.getOwnerId()));
        openOrders.stream()
                .filter(order -> order.getSide() != side && order.isOpen())
                .forEach(order -> ids.add(order.getOwnerId()));
        return ids;
    }

    /**
     * Se opera un jugador activo, con cotización y con su orden inicial (research D15).
     */
    public boolean isTradable() {
        return notTradableReason().isEmpty();
    }

    /**
     * Una compra (FR-015, FR-022 y research D10): se ejecuta en el acto contra la orden inicial y
     * las ventas pendientes, y lo que falta queda pendiente con su reserva al precio vigente. El
     * comprador reserva de una vez todo lo que necesita y cada operación se paga desde esa reserva.
     * Si el libre no alcanza, se rechaza sin cambiar nada.
     */
    public BookOutcome placeBuy(long ownerId, int quantity, Instant at) {
        AppUser buyer = accountOf(ownerId);
        buyer.requireCanTrade();
        requireTradable();
        requireNoOpenOrderOf(ownerId, OrderSide.SELL);
        BigDecimal price = quote.tokenPrice();
        MatchPlan plan = OrderMatcher.matchBuy(ownerId, quantity, Optional.of(initialOrder), openOrders, price);
        BigDecimal needed = plan.executedTotal().add(MarketMath.total(plan.remainder(), price));
        MarketOrder order = MarketOrder.buy(ownerId, playerId(), quantity, needed, price, at);
        PartyState before = stateOf(buyer);
        buyer.reserve(needed);
        BookOutcome.Builder outcome = new BookOutcome.Builder(player, quote, buyer, order);
        outcome.orderCreated(AuditEntry.orderCreated(order, player, AuditActor.user(ownerId),
                new PartyChange(before, stateOf(buyer)), at));
        plan.fills().forEach(fill -> executeTrade(outcome, order, fill.counterpart(), fill, ownerId, at));
        return outcome.build();
    }

    /**
     * Una venta (FR-011, FR-016, FR-023, FR-027 y research D10 y D11): se crea y compromete sus
     * tokens, se cruza en el acto con las compras pendientes de la más vieja a la más nueva al
     * precio vigente, y lo que falta queda pendiente con sus tokens comprometidos. Lo que una compra
     * no puede pagar se cancela por saldo insuficiente, y la reserva de cada compra cruzada se
     * vuelve a calcular para su remanente.
     */
    public BookOutcome placeSell(long ownerId, int quantity, Instant at) {
        AppUser seller = accountOf(ownerId);
        seller.requireCanTrade();
        requireTradable();
        requireInitialOrderSoldOut();
        requireNoOpenOrderOf(ownerId, OrderSide.BUY);
        Holding sellerHolding = holdingOf(ownerId);
        MarketOrder order = MarketOrder.sell(ownerId, playerId(), quantity, at);
        PartyState before = stateOf(seller);
        sellerHolding.commit(quantity);
        BookOutcome.Builder outcome = new BookOutcome.Builder(player, quote, seller, order);
        outcome.changed(sellerHolding);
        outcome.orderCreated(AuditEntry.orderCreated(order, player, AuditActor.user(ownerId),
                new PartyChange(before, stateOf(seller)), at));
        MatchPlan plan = OrderMatcher.matchSell(ownerId, quantity, openOrders, freeBalances(), quote.tokenPrice());
        plan.fills().forEach(fill -> executeTrade(outcome, fill.counterpart(), order, fill, ownerId, at));
        plan.shortfalls().forEach(shortfall -> cancelForShortfall(outcome, shortfall.order(), at));
        plan.fills().forEach(fill -> adjustReservation(fill.counterpart()));
        return outcome.build();
    }

    /**
     * Un cruce: el comprador paga desde la reserva de su compra, el vendedor cobra y los tokens
     * pasan de la venta (o del pendiente de la inicial) a la tenencia del comprador. Si la
     * operación cuesta más que lo reservado, porque el precio subió desde que se creó la compra, el
     * comprador reserva antes la diferencia desde su libre (research D11).
     *
     * @param actorId quien hizo el pedido: el comprador o el vendedor
     */
    private void executeTrade(BookOutcome.Builder outcome, MarketOrder buyOrder, MarketOrder sellOrder, Fill fill,
                              long actorId, Instant at) {
        AppUser buyer = accountOf(buyOrder.getOwnerId());
        AppUser seller = accountOf(sellOrder.getOwnerId());
        Holding buyerHolding = holdingOf(buyer.getId());
        PartyState buyerBefore = stateOf(buyer);
        PartyState sellerBefore = stateOf(seller);
        BigDecimal buyerBalanceBefore = buyer.getBalance();
        BigDecimal sellerBalanceBefore = seller.getBalance();

        reserveShortage(buyOrder, buyer, fill.total());
        buyOrder.fill(fill.quantity());
        buyOrder.consumeReservation(fill.total());
        buyer.payFromReserve(fill.total());
        buyerHolding.buy(fill.quantity(), fill.total());
        outcome.changed(buyerHolding);
        sellOrder.fill(fill.quantity());
        seller.receive(fill.total());
        BigDecimal realized = sellOrder.isInitial() ? null : sellFromHolding(outcome, seller, fill);

        Trade trade = new Trade(null, at, playerId(), buyOrder.getId(), sellOrder.getId(), buyer.getId(),
                seller.getId(), sellOrder.ownerType(), fill.quantity(), fill.tokenPrice(), fill.total(), quote.id(),
                buyerBalanceBefore, buyer.getBalance(), sellerBalanceBefore, seller.getBalance(),
                buyerBefore.tokens(), tokensOf(buyer), sellerBefore.tokens(), tokensOf(seller), realized);
        AuditEntry entry = AuditEntry.tradeExecuted(trade, player, AuditActor.user(actorId),
                new PartyChange(buyerBefore, stateOf(buyer)), new PartyChange(sellerBefore, stateOf(seller)));
        outcome.changed(buyer);
        outcome.changed(seller);
        outcome.tradeExecuted(trade, buyOrder, sellOrder, entry);
    }

    /**
     * Si la operación cuesta más que la reserva de la compra, el comprador reserva la diferencia
     * desde su libre. El plan ya verificó que le alcanza.
     */
    private static void reserveShortage(MarketOrder buyOrder, AppUser buyer, BigDecimal total) {
        BigDecimal shortage = total.subtract(buyOrder.getReservedCredits());
        if (shortage.signum() > 0) {
            buyer.reserve(shortage);
            buyOrder.addToReservation(shortage);
        }
    }

    /**
     * Cancela por saldo insuficiente lo que la compra no pudo pagar (FR-027) y le devuelve su
     * reserva al libre. El autor es el sistema.
     */
    private void cancelForShortfall(BookOutcome.Builder outcome, MarketOrder purchase, Instant at) {
        AppUser buyer = accountOf(purchase.getOwnerId());
        PartyState before = stateOf(buyer);
        buyer.release(purchase.cancelRemainder(CancelReason.INSUFFICIENT_CREDITS));
        outcome.changed(buyer);
        outcome.orderCancelled(purchase, AuditEntry.orderCancelled(purchase, player, AuditActor.system(),
                new PartyChange(before, stateOf(buyer)), at));
    }

    /**
     * Deja la reserva de una compra cruzada en la de su remanente al precio de reserva, o en lo que
     * su dueño pueda reservar si no alcanza (research D11). Si el precio bajó, lo que sobra vuelve al
     * libre. Una compra cancelada o ejecutada queda sin reserva.
     */
    private void adjustReservation(MarketOrder purchase) {
        AppUser buyer = accountOf(purchase.getOwnerId());
        BigDecimal change = purchase.adjustReservation(purchase.getReservedCredits().add(buyer.availableBalance()));
        if (change.signum() > 0) {
            buyer.reserve(change);
        } else if (change.signum() < 0) {
            buyer.release(change.negate());
        }
    }

    /**
     * Las ventas de usuarios esperan a que el superusuario venda todos sus tokens del jugador
     * (FR-011): no se crea nada.
     */
    private void requireInitialOrderSoldOut() {
        if (initialOrder.pending() > 0) {
            throw new SaleBlockedByInitialOrderException(initialOrder.pending());
        }
    }

    private Map<Long, BigDecimal> freeBalances() {
        Map<Long, BigDecimal> free = new HashMap<>();
        accounts.forEach((id, account) -> free.put(id, account.availableBalance()));
        return free;
    }

    /**
     * Un usuario vende tokens comprometidos en su venta: bajan su tenencia y sus comprometidos.
     *
     * @return su ganancia o pérdida realizada (FR-029)
     */
    private BigDecimal sellFromHolding(BookOutcome.Builder outcome, AppUser seller, Fill fill) {
        Holding sellerHolding = holdingOf(seller.getId());
        BigDecimal realized = sellerHolding.sell(fill.quantity(), fill.total());
        outcome.changed(sellerHolding);
        return realized;
    }

    private void requireTradable() {
        notTradableReason().ifPresent(reason -> {
            throw new PlayerNotTradableException(reason);
        });
    }

    private Optional<NotTradableReason> notTradableReason() {
        if (!player.active()) {
            return Optional.of(NotTradableReason.INACTIVE);
        }
        if (quote == null) {
            return Optional.of(NotTradableReason.WITHOUT_QUOTE);
        }
        if (initialOrder == null) {
            return Optional.of(NotTradableReason.WITHOUT_INITIAL_ORDER);
        }
        return Optional.empty();
    }

    /**
     * Un usuario no compra y vende el mismo jugador a la vez (FR-017).
     */
    private void requireNoOpenOrderOf(long ownerId, OrderSide side) {
        boolean pending = openOrders.stream()
                .anyMatch(order -> order.getOwnerId() == ownerId && order.getSide() == side && order.isOpen());
        if (pending) {
            throw new OppositeOrderPendingException(side);
        }
    }

    private AppUser accountOf(long userId) {
        AppUser account = accounts.get(userId);
        if (account == null) {
            throw new MarketInvariantException("La cuenta " + userId + " no está bloqueada en el libro del jugador "
                    + playerId() + ".");
        }
        return account;
    }

    /**
     * La tenencia del usuario; la de quien compra el jugador por primera vez nace vacía.
     */
    private Holding holdingOf(long ownerId) {
        return holdings.computeIfAbsent(ownerId, owner -> Holding.empty(owner, playerId()));
    }

    /**
     * Los tokens del jugador que tiene la cuenta: del superusuario, el pendiente de su orden inicial.
     */
    private int tokensOf(AppUser account) {
        if (initialOrder != null && initialOrder.getOwnerId() == account.getId()) {
            return initialOrder.pending();
        }
        Holding holding = holdings.get(account.getId());
        return holding == null ? 0 : holding.getQuantity();
    }

    private PartyState stateOf(AppUser account) {
        return PartyState.of(account, tokensOf(account));
    }

    private long playerId() {
        return player.id();
    }
}
