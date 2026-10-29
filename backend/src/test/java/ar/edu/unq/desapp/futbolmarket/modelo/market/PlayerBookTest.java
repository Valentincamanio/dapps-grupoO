package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.SUPERUSER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.costBasis;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.holding;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.inactivePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.quote;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.superuser;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OppositeOrderPendingException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.PlayerNotTradableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.TradingNotAllowedException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

class PlayerBookTest {

    private static final long BUYER = 10L;
    private static final long SELLER = 11L;
    private static final long PLAYER = 42L;
    private static final long QUOTE_ID = 300L;
    private static final long INITIAL_ID = 1L;
    private static final Instant CREATED = at("2026-08-17T09:00");
    private static final Instant NOW = at("2026-08-24T12:00");
    private static final Player KANE = activePlayer(PLAYER, "Harry Kane");
    private static final Quote QUOTE = quote(QUOTE_ID, PLAYER, "103.00", "0.300000", at("2026-08-24T00:00"));

    @Test
    void unaCompraDeDiezContraLaInicialPagaDiezCreditosAlSuperusuarioUS14() {
        AppUser buyer = user(BUYER, "1000.00");
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 100, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer, admin);

        BookOutcome outcome = book.placeBuy(BUYER, 10, NOW);

        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("990.00"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertThat(admin.getBalance()).isEqualTo(new BigDecimal("10.00"));
        assertThat(initial.pending()).isEqualTo(90);
        assertThat(outcome.order().status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(outcome.order().getReservedCredits()).isEqualByComparingTo("0.00");
        assertThat(outcome.order().getReservationPrice()).isEqualTo(new BigDecimal("1.0300"));
        assertThat(outcome.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.getOwnerId()).isEqualTo(BUYER);
            assertThat(holding.getQuantity()).isEqualTo(10);
            assertThat(holding.getBasis().averagePrice()).isEqualByComparingTo("1.0000");
        });
        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.quantity()).isEqualTo(10);
            assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0000");
            assertThat(trade.total()).isEqualTo(new BigDecimal("10.00"));
            assertThat(trade.quoteId()).isEqualTo(QUOTE_ID);
            assertThat(trade.sellerType()).isEqualTo(OwnerType.ADMIN);
            assertThat(trade.sellOrderId()).isEqualTo(INITIAL_ID);
            assertThat(trade.buyerBalanceBefore()).isEqualTo(new BigDecimal("1000.00"));
            assertThat(trade.buyerBalanceAfter()).isEqualTo(new BigDecimal("990.00"));
            assertThat(trade.sellerBalanceBefore()).isEqualTo(new BigDecimal("0.00"));
            assertThat(trade.sellerBalanceAfter()).isEqualTo(new BigDecimal("10.00"));
            assertThat(trade.buyerTokensBefore()).isZero();
            assertThat(trade.buyerTokensAfter()).isEqualTo(10);
            assertThat(trade.sellerTokensBefore()).isEqualTo(100);
            assertThat(trade.sellerTokensAfter()).isEqualTo(90);
            assertThat(trade.realizedProfit()).isNull();
        });
        assertThat(outcome.executedQuantity()).isEqualTo(10);
        assertThat(outcome.executedTotal()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void conTresEnLaInicialEjecutaTresYReservaElRemanenteAlPrecioVigenteUS15() {
        AppUser buyer = user(BUYER, "1000.00");
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 3, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer, admin);

        BookOutcome outcome = book.placeBuy(BUYER, 10, NOW);

        MarketOrder order = outcome.order();
        assertThat(order.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(order.getExecuted()).isEqualTo(3);
        assertThat(order.pending()).isEqualTo(7);
        assertThat(order.getReservedCredits()).isEqualTo(new BigDecimal("7.21"));
        assertThat(initial.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("997.00"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("7.21");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("989.79");
        assertThat(admin.getBalance()).isEqualTo(new BigDecimal("3.00"));
        assertThat(outcome.orders()).containsExactly(order, initial);
        assertThat(outcome.accounts()).containsExactly(buyer, admin);
    }

    @Test
    void sinSaldoLibreSuficienteRechazaSinCambiarNadaUS16() {
        AppUser buyer = user(BUYER, "5.00");
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 100, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer, admin);

        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("5.00"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertThat(admin.getBalance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(initial.pending()).isEqualTo(100);
    }

    @Test
    void loNecesarioSumaLoQueSeEjecutaYLaReservaDelRemanente() {
        AppUser buyer = user(BUYER, "10.20");
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 3, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer, admin);

        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,21 créditos y el saldo libre es 10,20.");
    }

    @Test
    void elAdministradorNoCompra() {
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 100, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), admin);

        assertThatThrownBy(() -> book.placeBuy(SUPERUSER_ID, 10, NOW))
                .isInstanceOf(TradingNotAllowedException.class)
                .hasMessage("El administrador no opera en el mercado.");
        assertThat(initial.pending()).isEqualTo(100);
    }

    @Test
    void unJugadorInactivoNoSeOpera() {
        PlayerBook book = book(inactivePlayer(PLAYER, "Harry Kane"), Optional.of(QUOTE),
                Optional.of(initialOrder(INITIAL_ID, PLAYER, 100, CREATED)), List.of(), List.of(),
                user(BUYER, "1000.00"), superuser("0.00"));

        assertThat(book.isTradable()).isFalse();
        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(PlayerNotTradableException.class)
                .hasMessage("El jugador no se puede operar: está inactivo.");
    }

    @Test
    void unJugadorSinCotizacionNoSeOpera() {
        PlayerBook book = book(KANE, Optional.empty(), Optional.of(initialOrder(INITIAL_ID, PLAYER, 100, CREATED)),
                List.of(), List.of(), user(BUYER, "1000.00"), superuser("0.00"));

        assertThat(book.isTradable()).isFalse();
        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(PlayerNotTradableException.class)
                .hasMessage("El jugador no se puede operar: todavía no tiene cotización.");
    }

    @Test
    void unJugadorSinOrdenInicialNoSeOpera() {
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.empty(), List.of(), List.of(),
                user(BUYER, "1000.00"));

        assertThat(book.isTradable()).isFalse();
        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(PlayerNotTradableException.class)
                .hasMessage("El jugador no se puede operar: todavía no tiene su orden inicial.");
    }

    @Test
    void unJugadorActivoConCotizacionYOrdenInicialSeOpera() {
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initialOrder(INITIAL_ID, PLAYER, 0, CREATED)),
                List.of(), List.of(), user(BUYER, "1000.00"));

        assertThat(book.isTradable()).isTrue();
    }

    @Test
    void conUnaVentaPropiaPendienteNoCompra() {
        AppUser buyer = user(BUYER, "1000.00");
        MarketOrder ownSale = sell(5L, BUYER, PLAYER, 4, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initialOrder(INITIAL_ID, PLAYER, 0, CREATED)),
                List.of(ownSale), List.of(holding(20L, BUYER, PLAYER, costBasis(10, "10.00"), 4)), buyer);

        assertThatThrownBy(() -> book.placeBuy(BUYER, 10, NOW))
                .isInstanceOf(OppositeOrderPendingException.class)
                .hasMessage("Ya tiene una orden de venta pendiente de este jugador.");
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
    }

    @Test
    void conLaInicialAgotadaCompraAOtroUsuarioAlPrecioVigenteYElVendedorRealizaSuGanancia() {
        AppUser buyer = user(BUYER, "1000.00");
        AppUser seller = user(SELLER, "990.00");
        MarketOrder sale = sell(5L, SELLER, PLAYER, 5, CREATED);
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 5);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initialOrder(INITIAL_ID, PLAYER, 0, CREATED)),
                List.of(sale), List.of(sellerHolding), buyer, seller);

        BookOutcome outcome = book.placeBuy(BUYER, 5, NOW);

        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("994.85"));
        assertThat(seller.getBalance()).isEqualTo(new BigDecimal("995.15"));
        assertThat(sale.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(sellerHolding.getQuantity()).isEqualTo(5);
        assertThat(sellerHolding.getCommitted()).isZero();
        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0300");
            assertThat(trade.total()).isEqualTo(new BigDecimal("5.15"));
            assertThat(trade.sellerType()).isEqualTo(OwnerType.USER);
            assertThat(trade.realizedProfit()).isEqualByComparingTo("0.15");
            assertThat(trade.sellerTokensBefore()).isEqualTo(10);
            assertThat(trade.sellerTokensAfter()).isEqualTo(5);
        });
        assertThat(outcome.holdings()).extracting(Holding::getOwnerId).containsExactly(BUYER, SELLER);
    }

    @Test
    void laAuditoriaSaleEnElOrdenDeLosHechosConElAntesYElDespuesDeCadaParte() {
        AppUser buyer = user(BUYER, "1000.00");
        AppUser admin = superuser("0.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 3, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer, admin);

        List<AuditEntry> entries = book.placeBuy(BUYER, 10, NOW).auditEntries();

        assertThat(entries).extracting(AuditEntry::eventType)
                .containsExactly(AuditEventType.ORDER_CREATED, AuditEventType.TRADE_EXECUTED);
        AuditEntry created = entries.get(0);
        assertThat(created.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(created.actorId()).isEqualTo(BUYER);
        assertThat(created.primary().before())
                .isEqualTo(new PartyState(BUYER, new BigDecimal("1000.00"), new BigDecimal("0.00"), 0));
        assertThat(created.primary().after())
                .isEqualTo(new PartyState(BUYER, new BigDecimal("989.79"), new BigDecimal("10.21"), 0));
        assertThat(created.detail()).isEqualTo("Orden de compra de 10 tokens de Harry Kane con 10,21 créditos reservados");
        AuditEntry trade = entries.get(1);
        assertThat(trade.primary().before())
                .isEqualTo(new PartyState(BUYER, new BigDecimal("989.79"), new BigDecimal("10.21"), 0));
        assertThat(trade.primary().after())
                .isEqualTo(new PartyState(BUYER, new BigDecimal("989.79"), new BigDecimal("7.21"), 3));
        assertThat(trade.secondary().before())
                .isEqualTo(new PartyState(SUPERUSER_ID, new BigDecimal("0.00"), new BigDecimal("0.00"), 3));
        assertThat(trade.secondary().after())
                .isEqualTo(new PartyState(SUPERUSER_ID, new BigDecimal("3.00"), new BigDecimal("0.00"), 0));
        assertThat(trade.detail()).isEqualTo("Compra de 3 tokens de Harry Kane a 1,0000 por 3,00 contra la orden inicial");
    }

    @Test
    void losIdsGuardadosLleganALasOperacionesYALaAuditoria() {
        AppUser buyer = user(BUYER, "1000.00");
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 100, CREATED);
        BookOutcome outcome = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(), buyer,
                superuser("0.00")).placeBuy(BUYER, 10, NOW);
        MarketOrder savedOrder = withId(outcome.order(), 77L);

        BookOutcome withOrders = outcome.withSavedOrders(List.of(savedOrder, initial));
        Trade savedTrade = withId(withOrders.trades().getFirst(), 500L);
        BookOutcome withTrades = withOrders.withSavedTrades(List.of(savedTrade));

        assertThat(outcome.trades().getFirst().buyOrderId()).isNull();
        assertThat(withTrades.order()).isSameAs(savedOrder);
        assertThat(withTrades.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.id()).isEqualTo(500L);
            assertThat(trade.buyOrderId()).isEqualTo(77L);
            assertThat(trade.sellOrderId()).isEqualTo(INITIAL_ID);
        });
        assertThat(withTrades.auditEntries()).extracting(AuditEntry::orderId).containsExactly(77L, 77L);
        assertThat(withTrades.auditEntries()).extracting(AuditEntry::tradeId).containsExactly(null, 500L);
    }

    @Test
    void unaCompraBloqueaAlQuePideAlSuperusuarioConPendienteYALosDuenosDeLasVentas() {
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 3, CREATED);
        List<MarketOrder> open = List.of(sell(5L, 30L, PLAYER, 2, CREATED),
                MarketFixtures.buy(6L, 40L, PLAYER, 2, "1.0300", CREATED));

        assertThat(PlayerBook.accountsToLock(BUYER, OrderSide.BUY, Optional.of(initial), open))
                .containsExactly(SUPERUSER_ID, BUYER, 30L);
        assertThat(PlayerBook.accountsToLock(BUYER, OrderSide.BUY,
                Optional.of(initialOrder(INITIAL_ID, PLAYER, 0, CREATED)), open))
                .containsExactly(BUYER, 30L);
    }

    private static PlayerBook book(Player player, Optional<Quote> quote, Optional<MarketOrder> initial,
                                   List<MarketOrder> open, List<Holding> holdings, AppUser... accounts) {
        Map<Long, AppUser> byId = Stream.of(accounts).collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return new PlayerBook(player, quote, initial, open, byId, holdings);
    }

    private static MarketOrder withId(MarketOrder order, long id) {
        return MarketOrder.reconstitute(new MarketOrder.State(id, order.getSide(), order.getOwnerId(),
                order.getPlayerId(), order.getRequested(), order.getExecuted(), order.getCancelled(),
                order.getReservedCredits(), order.getReservationPrice(), order.getCreatedAt(), order.isInitial(),
                order.getCancelReason()));
    }

    private static Trade withId(Trade trade, long id) {
        return new Trade(id, trade.executedAt(), trade.playerId(), trade.buyOrderId(), trade.sellOrderId(),
                trade.buyerId(), trade.sellerId(), trade.sellerType(), trade.quantity(), trade.tokenPrice(),
                trade.total(), trade.quoteId(), trade.buyerBalanceBefore(), trade.buyerBalanceAfter(),
                trade.sellerBalanceBefore(), trade.sellerBalanceAfter(), trade.buyerTokensBefore(),
                trade.buyerTokensAfter(), trade.sellerTokensBefore(), trade.sellerTokensAfter(),
                trade.realizedProfit());
    }
}
