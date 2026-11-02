package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * Lo que resultó de un pedido sobre el libro de un jugador (data-model 2.10): la orden del pedido,
 * las órdenes, cuentas y tenencias que cambiaron, las operaciones y la auditoría, en el orden de
 * los hechos. También expone el jugador, la cotización usada y la cuenta del pedido, para armar la
 * respuesta.
 *
 * <p>La orden del pedido no tiene id hasta guardarse, y las operaciones y la auditoría lo
 * necesitan. Por eso el libro registra a qué orden y a qué operación apunta cada una (su posición
 * en {@link #orders()} y en {@link #trades()}), y quien guarda pasa lo guardado a
 * {@link #withSavedOrders} y a {@link #withSavedTrades}, que devuelven un resultado nuevo con los
 * ids puestos. {@link Trade} y {@link AuditEntry} siguen inmutables: se copian.</p>
 */
public final class BookOutcome {
    private static final int NO_TRADE = -1;

    private final Player player;
    private final Quote quote;
    private final AppUser account;
    private final List<MarketOrder> orders;
    private final List<AppUser> accounts;
    private final List<Holding> holdings;
    private final List<LinkedTrade> trades;
    private final List<LinkedEntry> auditEntries;

    private BookOutcome(Player player, Quote quote, AppUser account, List<MarketOrder> orders, List<AppUser> accounts,
                        List<Holding> holdings, List<LinkedTrade> trades, List<LinkedEntry> auditEntries) {
        this.player = player;
        this.quote = quote;
        this.account = account;
        this.orders = List.copyOf(orders);
        this.accounts = List.copyOf(accounts);
        this.holdings = List.copyOf(holdings);
        this.trades = List.copyOf(trades);
        this.auditEntries = List.copyOf(auditEntries);
    }

    public Player player() {
        return player;
    }

    public Quote quote() {
        return quote;
    }

    /**
     * La cuenta de quien hizo el pedido, con sus saldos después del pedido.
     */
    public AppUser account() {
        return account;
    }

    /**
     * La orden del pedido.
     */
    public MarketOrder order() {
        return orders.getFirst();
    }

    /**
     * Las órdenes que cambiaron, la del pedido primero.
     */
    public List<MarketOrder> orders() {
        return orders;
    }

    public List<AppUser> accounts() {
        return accounts;
    }

    public List<Holding> holdings() {
        return holdings;
    }

    /**
     * Las operaciones, en el orden en que se ejecutaron.
     */
    public List<Trade> trades() {
        return trades.stream().map(LinkedTrade::trade).toList();
    }

    /**
     * Los registros de auditoría, en el orden de los hechos.
     */
    public List<AuditEntry> auditEntries() {
        return auditEntries.stream().map(LinkedEntry::entry).toList();
    }

    public int executedQuantity() {
        return trades.stream().mapToInt(linked -> linked.trade().quantity()).sum();
    }

    public BigDecimal executedTotal() {
        return trades.stream().map(linked -> linked.trade().total()).reduce(MarketMath.ZERO_CREDITS, BigDecimal::add);
    }

    /**
     * El mismo resultado con las órdenes ya guardadas, en el orden de {@link #orders()}: las
     * operaciones y la auditoría toman sus ids.
     */
    public BookOutcome withSavedOrders(List<MarketOrder> saved) {
        requireSameSize(orders.size(), saved.size());
        List<LinkedTrade> linkedTrades = trades.stream()
                .map(linked -> linked.withTrade(linked.trade().withOrderIds(
                        saved.get(linked.buyOrder()).getId(), saved.get(linked.sellOrder()).getId())))
                .toList();
        List<LinkedEntry> linkedEntries = auditEntries.stream()
                .map(linked -> linked.withEntry(linked.entry().withReferences(
                        saved.get(linked.order()).getId(), linked.entry().tradeId())))
                .toList();
        return new BookOutcome(player, quote, account, saved, accounts, holdings, linkedTrades, linkedEntries);
    }

    /**
     * El mismo resultado con las operaciones ya guardadas, en el orden de {@link #trades()}: la
     * auditoría de cada una toma su id.
     */
    public BookOutcome withSavedTrades(List<Trade> saved) {
        requireSameSize(trades.size(), saved.size());
        List<LinkedTrade> linkedTrades = new ArrayList<>();
        for (int index = 0; index < saved.size(); index++) {
            linkedTrades.add(trades.get(index).withTrade(saved.get(index)));
        }
        List<LinkedEntry> linkedEntries = auditEntries.stream()
                .map(linked -> linked.trade() == NO_TRADE ? linked : linked.withEntry(linked.entry().withReferences(
                        linked.entry().orderId(), saved.get(linked.trade()).id())))
                .toList();
        return new BookOutcome(player, quote, account, orders, accounts, holdings, linkedTrades, linkedEntries);
    }

    private static void requireSameSize(int expected, int actual) {
        if (expected != actual) {
            throw new MarketInvariantException("Se guardaron " + actual + " elementos de " + expected + ".");
        }
    }

    /**
     * Una operación con la posición de su compra y de su venta en las órdenes del resultado.
     */
    private record LinkedTrade(Trade trade, int buyOrder, int sellOrder) {
        LinkedTrade withTrade(Trade other) {
            return new LinkedTrade(other, buyOrder, sellOrder);
        }
    }

    /**
     * Un registro con la posición de su orden y de su operación ({@link #NO_TRADE} si no tiene).
     */
    private record LinkedEntry(AuditEntry entry, int order, int trade) {
        LinkedEntry withEntry(AuditEntry other) {
            return new LinkedEntry(other, order, trade);
        }
    }

    /**
     * Lo arma {@link PlayerBook} a medida que aplica el pedido. Cada cuenta, tenencia y orden que
     * cambia figura una sola vez, en el orden en que cambió por primera vez.
     */
    static final class Builder {
        private final Player player;
        private final Quote quote;
        private final AppUser account;
        private final List<MarketOrder> orders = new ArrayList<>();
        private final Map<Long, AppUser> accounts = new LinkedHashMap<>();
        private final Map<Long, Holding> holdings = new LinkedHashMap<>();
        private final List<LinkedTrade> trades = new ArrayList<>();
        private final List<LinkedEntry> auditEntries = new ArrayList<>();

        Builder(Player player, Quote quote, AppUser account, MarketOrder order) {
            this.player = player;
            this.quote = quote;
            this.account = account;
            orders.add(order);
            changed(account);
        }

        void changed(AppUser changed) {
            accounts.putIfAbsent(changed.getId(), changed);
        }

        void changed(Holding changed) {
            holdings.putIfAbsent(changed.getOwnerId(), changed);
        }

        void orderCreated(AuditEntry entry) {
            auditEntries.add(new LinkedEntry(entry, 0, NO_TRADE));
        }

        /**
         * La operación y su registro, que apunta a la orden del pedido.
         */
        void tradeExecuted(Trade trade, MarketOrder buyOrder, MarketOrder sellOrder, AuditEntry entry) {
            trades.add(new LinkedTrade(trade, indexOf(buyOrder), indexOf(sellOrder)));
            auditEntries.add(new LinkedEntry(entry, 0, trades.size() - 1));
        }

        /**
         * El registro de una orden cancelada, que apunta a esa orden; si todavía no había cambiado,
         * pasa a estar entre las que cambiaron.
         */
        void orderCancelled(MarketOrder order, AuditEntry entry) {
            auditEntries.add(new LinkedEntry(entry, indexOf(order), NO_TRADE));
        }

        BookOutcome build() {
            return new BookOutcome(player, quote, account, orders, List.copyOf(accounts.values()),
                    List.copyOf(holdings.values()), trades, auditEntries);
        }

        /**
         * La posición de la orden entre las que cambiaron; si es la primera vez que cambia, la suma.
         * Se compara por identidad: la orden del pedido todavía no tiene id.
         */
        private int indexOf(MarketOrder order) {
            for (int index = 0; index < orders.size(); index++) {
                if (orders.get(index) == order) {
                    return index;
                }
            }
            orders.add(order);
            return orders.size() - 1;
        }
    }
}
