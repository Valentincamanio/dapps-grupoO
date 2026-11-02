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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientTokensException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OppositeOrderPendingException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotCancellableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotOwnedException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.PlayerNotTradableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.SaleBlockedByInitialOrderException;
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
    private static final long USER_A = 12L;
    private static final long USER_C = 13L;
    private static final long USER_D = 14L;
    private static final Instant LATER = at("2026-08-18T09:00");
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

    @Test
    void mientrasLaInicialTieneUnTokenNoSeVendeNiSeCreaNadaUS41() {
        AppUser seller = user(SELLER, "990.00");
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 1, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(sellerHolding),
                seller);

        assertThatThrownBy(() -> book.placeSell(SELLER, 5, NOW))
                .isInstanceOf(SaleBlockedByInitialOrderException.class)
                .hasMessage("Todavía no se puede vender: al administrador le queda 1 token de este jugador.");
        assertThat(sellerHolding.getCommitted()).isZero();
        assertThat(seller.getBalance()).isEqualTo(new BigDecimal("990.00"));
        assertThat(initial.pending()).isEqualTo(1);
    }

    @Test
    void conLaInicialAgotadaLaVentaSeCreaPendienteYComprometeSusTokensUS42() {
        AppUser seller = user(SELLER, "990.00");
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(), List.of(sellerHolding),
                seller);

        BookOutcome outcome = book.placeSell(SELLER, 5, NOW);

        MarketOrder order = outcome.order();
        assertThat(order.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.pending()).isEqualTo(5);
        assertThat(order.getReservedCredits()).isEqualByComparingTo("0.00");
        assertThat(sellerHolding.getCommitted()).isEqualTo(5);
        assertThat(sellerHolding.freeTokens()).isEqualTo(5);
        assertThat(outcome.trades()).isEmpty();
        assertThat(outcome.holdings()).containsExactly(sellerHolding);
        assertThat(outcome.auditEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CREATED);
            assertThat(entry.actorId()).isEqualTo(SELLER);
            assertThat(entry.side()).isEqualTo(OrderSide.SELL);
            assertThat(entry.detail()).isEqualTo("Orden de venta de 5 tokens de Harry Kane");
        });
    }

    @Test
    void sinVentasUnaCompraQuedaPendienteYReservaAlPrecioVigenteUS43() {
        AppUser buyer = user(USER_A, "1000.00");
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(), List.of(), buyer);

        BookOutcome outcome = book.placeBuy(USER_A, 10, NOW);

        assertThat(outcome.order().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(outcome.order().pending()).isEqualTo(10);
        assertThat(outcome.order().getReservedCredits()).isEqualTo(new BigDecimal("10.30"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("10.30");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("989.70");
    }

    @Test
    void unaVentaDeCuatroACientoCincoPagaCuatroVeinteYAjustaLaReservaDelRemanenteUS44() {
        AppUser buyer = user(USER_A, "1000.00", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook book = book(KANE, Optional.of(quoteOf("105.00")), Optional.of(soldOut()), List.of(purchase),
                List.of(sellerHolding), buyer, seller);

        BookOutcome outcome = book.placeSell(SELLER, 4, NOW);

        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.quantity()).isEqualTo(4);
            assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0500");
            assertThat(trade.total()).isEqualTo(new BigDecimal("4.20"));
            assertThat(trade.sellerType()).isEqualTo(OwnerType.USER);
            assertThat(trade.buyerId()).isEqualTo(USER_A);
            assertThat(trade.sellerId()).isEqualTo(SELLER);
            assertThat(trade.realizedProfit()).isEqualByComparingTo("0.20");
        });
        assertThat(purchase.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(purchase.pending()).isEqualTo(6);
        assertThat(purchase.getReservedCredits()).isEqualTo(new BigDecimal("6.18"));
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("995.80"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("6.18");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("989.62");
        assertThat(seller.getBalance()).isEqualTo(new BigDecimal("1004.20"));
        assertThat(outcome.order().status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(sellerHolding.getQuantity()).isEqualTo(6);
        assertThat(sellerHolding.getCommitted()).isZero();
        assertThat(outcome.orders()).containsExactly(outcome.order(), purchase);
        assertThat(outcome.accounts()).containsExactly(seller, buyer);
    }

    @Test
    void unaVentaDeOchoContraUnaCompraDeCincoDejaTresPendientesComprometidosUS45() {
        AppUser buyer = user(USER_A, "1000.00", "5.15");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 5, "1.0300", CREATED);
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(purchase),
                List.of(sellerHolding), buyer, seller);

        BookOutcome outcome = book.placeSell(SELLER, 8, NOW);

        assertThat(outcome.executedQuantity()).isEqualTo(5);
        assertThat(outcome.order().status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(outcome.order().pending()).isEqualTo(3);
        assertThat(purchase.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(sellerHolding.getQuantity()).isEqualTo(5);
        assertThat(sellerHolding.getCommitted()).isEqualTo(3);
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertOneSideOpen(List.of(purchase), outcome);
    }

    @Test
    void conDosComprasSeCompletaLaMasViejaYElRestoVaALaOtraUS46() {
        AppUser fromA = user(USER_A, "1000.00", "5.15");
        AppUser fromC = user(USER_C, "1000.00", "5.15");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchaseA = MarketFixtures.buy(5L, USER_A, PLAYER, 5, "1.0300", CREATED);
        MarketOrder purchaseC = MarketFixtures.buy(6L, USER_C, PLAYER, 5, "1.0300", LATER);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(purchaseC, purchaseA),
                List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0)), fromA, fromC, seller);

        BookOutcome outcome = book.placeSell(SELLER, 7, NOW);

        assertThat(outcome.trades()).extracting(Trade::buyerId).containsExactly(USER_A, USER_C);
        assertThat(outcome.trades()).extracting(Trade::quantity).containsExactly(5, 2);
        assertThat(purchaseA.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(purchaseC.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(purchaseC.pending()).isEqualTo(3);
        assertThat(purchaseC.getReservedCredits()).isEqualTo(new BigDecimal("3.09"));
        assertThat(fromC.getReserved()).isEqualByComparingTo("3.09");
    }

    @Test
    void conDosVentasUnaCompraSeEjecutaPrimeroContraLaMasViejaUS47() {
        AppUser buyer = user(USER_A, "1000.00");
        MarketOrder fromB = sell(5L, SELLER, PLAYER, 3, CREATED);
        MarketOrder fromD = sell(6L, USER_D, PLAYER, 3, LATER);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(fromD, fromB),
                List.of(holding(20L, SELLER, PLAYER, costBasis(3, "3.00"), 3),
                        holding(21L, USER_D, PLAYER, costBasis(3, "3.00"), 3)),
                buyer, user(SELLER, "1000.00"), user(USER_D, "1000.00"));

        BookOutcome outcome = book.placeBuy(USER_A, 4, NOW);

        assertThat(outcome.trades()).extracting(Trade::sellerId).containsExactly(SELLER, USER_D);
        assertThat(outcome.trades()).extracting(Trade::quantity).containsExactly(3, 1);
        assertThat(fromB.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(fromD.pending()).isEqualTo(2);
    }

    @Test
    void diezTokensAUnoCeroTrescientosCuarentaYCincoSonDiezTreintaYCincoParaLasDosPartesUS48() {
        AppUser buyer = user(USER_A, "1000.00");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder sale = sell(5L, SELLER, PLAYER, 10, CREATED);
        PlayerBook book = book(KANE, Optional.of(quoteOf("103.45")), Optional.of(soldOut()), List.of(sale),
                List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 10)), buyer, seller);

        BookOutcome outcome = book.placeBuy(USER_A, 10, NOW);

        assertThat(outcome.executedTotal()).isEqualTo(new BigDecimal("10.35"));
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("989.65"));
        assertThat(seller.getBalance()).isEqualTo(new BigDecimal("1010.35"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
    }

    @Test
    void conElPrecioAUnoDiezYSinLibreSeEjecutanNueveYSeCancelaUnoPorSaldoUS49() {
        AppUser buyer = user(USER_A, "10.30", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook book = book(KANE, Optional.of(quoteOf("110.00")), Optional.of(soldOut()), List.of(purchase),
                List.of(sellerHolding), buyer, seller);

        BookOutcome outcome = book.placeSell(SELLER, 10, NOW);

        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.quantity()).isEqualTo(9);
            assertThat(trade.total()).isEqualTo(new BigDecimal("9.90"));
        });
        assertThat(purchase.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(purchase.getExecuted()).isEqualTo(9);
        assertThat(purchase.getCancelled()).isEqualTo(1);
        assertThat(purchase.getCancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
        assertThat(purchase.getReservedCredits()).isEqualByComparingTo("0.00");
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("0.40"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("0.40");
        assertThat(outcome.order().pending()).isEqualTo(1);
        assertThat(sellerHolding.getCommitted()).isEqualTo(1);
        List<AuditEntry> entries = outcome.auditEntries();
        assertThat(entries).extracting(AuditEntry::eventType).containsExactly(AuditEventType.ORDER_CREATED,
                AuditEventType.TRADE_EXECUTED, AuditEventType.ORDER_CANCELLED);
        AuditEntry cancelled = entries.get(2);
        assertThat(cancelled.actorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(cancelled.actorId()).isNull();
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
        assertThat(cancelled.quantity()).isEqualTo(1);
        assertThat(cancelled.primary().before())
                .isEqualTo(new PartyState(USER_A, new BigDecimal("0.00"), new BigDecimal("0.40"), 9));
        assertThat(cancelled.primary().after())
                .isEqualTo(new PartyState(USER_A, new BigDecimal("0.40"), new BigDecimal("0.00"), 9));
        assertThat(cancelled.detail())
                .isEqualTo("Cancelación de 1 token de la orden de compra de Harry Kane por saldo insuficiente");
    }

    @Test
    void conSeisComprometidosEnUnaVentaSoloLeQuedanCuatroLibresUS410() {
        AppUser seller = user(SELLER, "1000.00");
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 6);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()),
                List.of(sell(5L, SELLER, PLAYER, 6, CREATED)), List.of(sellerHolding), seller);

        assertThatThrownBy(() -> book.placeSell(SELLER, 5, NOW))
                .isInstanceOf(InsufficientTokensException.class)
                .hasMessage("Tokens insuficientes: tiene 4 tokens libres de este jugador.");
        assertThat(sellerHolding.getCommitted()).isEqualTo(6);
    }

    @Test
    void sinTenenciaNoSeVende() {
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(), List.of(),
                user(SELLER, "1000.00"));

        assertThatThrownBy(() -> book.placeSell(SELLER, 1, NOW))
                .isInstanceOf(InsufficientTokensException.class)
                .hasMessage("Tokens insuficientes: tiene 0 tokens libres de este jugador.");
    }

    @Test
    void unUsuarioNoCompraYVendeElMismoJugadorALaVezUS411() {
        AppUser trader = user(USER_A, "1000.00", "5.15");
        Holding holding = holding(20L, USER_A, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook withPurchase = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()),
                List.of(MarketFixtures.buy(5L, USER_A, PLAYER, 5, "1.0300", CREATED)), List.of(holding), trader);
        PlayerBook withSale = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()),
                List.of(sell(6L, USER_A, PLAYER, 5, CREATED)),
                List.of(holding(21L, USER_A, PLAYER, costBasis(10, "10.00"), 5)), user(USER_A, "1000.00"));

        assertThatThrownBy(() -> withPurchase.placeSell(USER_A, 5, NOW))
                .isInstanceOf(OppositeOrderPendingException.class)
                .hasMessage("Ya tiene una orden de compra pendiente de este jugador.");
        assertThatThrownBy(() -> withSale.placeBuy(USER_A, 5, NOW))
                .isInstanceOf(OppositeOrderPendingException.class)
                .hasMessage("Ya tiene una orden de venta pendiente de este jugador.");
        assertThat(holding.getCommitted()).isZero();
    }

    @Test
    void elAdministradorNoVende() {
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(), List.of(),
                superuser("100.00"));

        assertThatThrownBy(() -> book.placeSell(SUPERUSER_ID, 1, NOW))
                .isInstanceOf(TradingNotAllowedException.class);
    }

    @Test
    void unJugadorInactivoNoSeVende() {
        PlayerBook book = book(inactivePlayer(PLAYER, "Harry Kane"), Optional.of(QUOTE), Optional.of(soldOut()),
                List.of(), List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0)), user(SELLER, "1000.00"));

        assertThatThrownBy(() -> book.placeSell(SELLER, 1, NOW))
                .isInstanceOf(PlayerNotTradableException.class)
                .hasMessage("El jugador no se puede operar: está inactivo.");
    }

    @Test
    void siPuedePagarLoQueSeEjecutaElRemanenteQuedaConLoQueAlcanzaSinCancelarElHuecoDeD11() {
        AppUser buyer = user(USER_A, "10.30", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        PlayerBook book = book(KANE, Optional.of(quoteOf("110.00")), Optional.of(soldOut()), List.of(purchase),
                List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0)), buyer, seller);

        BookOutcome outcome = book.placeSell(SELLER, 4, NOW);

        assertThat(outcome.executedTotal()).isEqualTo(new BigDecimal("4.40"));
        assertThat(purchase.pending()).isEqualTo(6);
        assertThat(purchase.getCancelled()).isZero();
        assertThat(purchase.getReservedCredits()).isEqualTo(new BigDecimal("5.90"));
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("5.90"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("5.90");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("0.00");
        assertThat(outcome.auditEntries()).extracting(AuditEntry::eventType)
                .doesNotContain(AuditEventType.ORDER_CANCELLED);
    }

    @Test
    void unaCompraQueSeEjecutaMasBarataQueSuReservaDevuelveLaDiferenciaAlLibre() {
        AppUser buyer = user(USER_A, "1000.00", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        PlayerBook book = book(KANE, Optional.of(quoteOf("100.00")), Optional.of(soldOut()), List.of(purchase),
                List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0)), buyer, seller);

        book.placeSell(SELLER, 10, NOW);

        assertThat(purchase.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(purchase.getReservedCredits()).isEqualByComparingTo("0.00");
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("990.00"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertThat(buyer.availableBalance()).isEqualByComparingTo("990.00");
    }

    @Test
    void unaCompraQueSeEjecutaMasCaraQueSuReservaTomaLaDiferenciaDelLibre() {
        AppUser buyer = user(USER_A, "1000.00", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        PlayerBook book = book(KANE, Optional.of(quoteOf("110.00")), Optional.of(soldOut()), List.of(purchase),
                List.of(holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0)), buyer, seller);

        book.placeSell(SELLER, 10, NOW);

        assertThat(purchase.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("989.00"));
        assertThat(buyer.getReserved()).isEqualByComparingTo("0.00");
        assertThat(seller.getBalance()).isEqualTo(new BigDecimal("1011.00"));
    }

    @Test
    void venderTodoDejaLaTenenciaEnCeroYLaRecompraArrancaUnPromedioNuevo() {
        AppUser buyer = user(USER_A, "1000.00", "10.30");
        AppUser seller = user(SELLER, "1000.00");
        Holding sellerHolding = holding(20L, SELLER, PLAYER, costBasis(10, "10.00"), 0);
        PlayerBook first = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()),
                List.of(MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED)), List.of(sellerHolding),
                buyer, seller);
        first.placeSell(SELLER, 10, NOW);
        PlayerBook second = book(KANE, Optional.of(quoteOf("110.00")), Optional.of(soldOut()),
                List.of(sell(6L, USER_D, PLAYER, 5, CREATED)),
                List.of(sellerHolding, holding(21L, USER_D, PLAYER, costBasis(5, "5.00"), 5)),
                seller, user(USER_D, "1000.00"));

        second.placeBuy(SELLER, 5, NOW);

        assertThat(sellerHolding.getQuantity()).isEqualTo(5);
        assertThat(sellerHolding.getBasis().averagePrice()).isEqualByComparingTo("1.1000");
        assertThat(sellerHolding.getBasis().invested()).isEqualByComparingTo("5.50");
    }

    @Test
    void elLibroNuncaQuedaConPendientesDeLosDosLados() {
        AppUser buyer = user(USER_A, "1000.00");
        MarketOrder sale = sell(5L, SELLER, PLAYER, 3, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(sale),
                List.of(holding(20L, SELLER, PLAYER, costBasis(3, "3.00"), 3)), buyer, user(SELLER, "1000.00"));

        BookOutcome outcome = book.placeBuy(USER_A, 10, NOW);

        assertThat(outcome.order().pending()).isEqualTo(7);
        assertOneSideOpen(List.of(sale), outcome);
    }

    @Test
    void cancelarUnaCompraParcialDevuelveSuReservaAlLibreYConservaLoEjecutadoUS67() {
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        purchase.fill(4);
        purchase.consumeReservation(new BigDecimal("4.12"));
        AppUser buyer = user(USER_A, "995.88", "6.18");
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(purchase),
                List.of(holding(20L, USER_A, PLAYER, costBasis(4, "4.12"), 0)), buyer);

        BookOutcome outcome = book.cancel(purchase, USER_A, NOW);

        assertThat(outcome.order()).isSameAs(purchase);
        assertThat(purchase.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(purchase.getExecuted()).isEqualTo(4);
        assertThat(purchase.getCancelled()).isEqualTo(6);
        assertThat(purchase.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
        assertThat(buyer.getBalance()).isEqualTo(new BigDecimal("995.88"));
        assertThat(buyer.getReserved()).isEqualTo(new BigDecimal("0.00"));
        assertThat(outcome.accounts()).containsExactly(buyer);
        assertThat(outcome.trades()).isEmpty();
        assertThat(outcome.auditEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CANCELLED);
            assertThat(entry.actorType()).isEqualTo(AuditActorType.USER);
            assertThat(entry.actorId()).isEqualTo(USER_A);
            assertThat(entry.quantity()).isEqualTo(6);
            assertThat(entry.cancelReason()).isEqualTo(CancelReason.USER_REQUEST);
            assertThat(entry.primary().before())
                    .isEqualTo(new PartyState(USER_A, new BigDecimal("989.70"), new BigDecimal("6.18"), 4));
            assertThat(entry.primary().after())
                    .isEqualTo(new PartyState(USER_A, new BigDecimal("995.88"), new BigDecimal("0.00"), 4));
        });
    }

    @Test
    void cancelarUnaVentaLiberaSusTokensComprometidosUS68() {
        MarketOrder sale = sell(6L, SELLER, PLAYER, 5, CREATED);
        AppUser seller = user(SELLER, "1000.00");
        Holding sellerHolding = holding(21L, SELLER, PLAYER, costBasis(10, "10.00"), 5);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(sale),
                List.of(sellerHolding), seller);

        BookOutcome outcome = book.cancel(sale, SELLER, NOW);

        assertThat(sale.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sellerHolding.getCommitted()).isZero();
        assertThat(sellerHolding.freeTokens()).isEqualTo(10);
        assertThat(outcome.holdings()).containsExactly(sellerHolding);
        assertThat(seller.availableBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(outcome.auditEntries()).singleElement()
                .satisfies(entry -> assertThat(entry.detail())
                        .isEqualTo("Cancelación de 5 tokens de la orden de venta de Harry Kane a pedido del usuario"));
    }

    @Test
    void unaOrdenEjecutadaNoSeCancelaYNoCambiaNadaUS69() {
        MarketOrder sale = sell(6L, SELLER, PLAYER, 5, CREATED);
        sale.fill(5);
        Holding sellerHolding = holding(21L, SELLER, PLAYER, costBasis(5, "5.00"), 0);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(),
                List.of(sellerHolding), user(SELLER, "1005.15"));

        assertThatThrownBy(() -> book.cancel(sale, SELLER, NOW))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("La orden ya está ejecutada.");
        assertThat(sale.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(sellerHolding.getCommitted()).isZero();
    }

    @Test
    void unaOrdenCanceladaNoSeVuelveACancelarUS69() {
        MarketOrder purchase = MarketFixtures.buy(5L, USER_A, PLAYER, 10, "1.0300", CREATED);
        purchase.cancelRemainder(CancelReason.USER_REQUEST);
        AppUser buyer = user(USER_A, "1000.00");
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(), List.of(), buyer);

        assertThatThrownBy(() -> book.cancel(purchase, USER_A, NOW))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("La orden ya está cancelada.");
        assertThat(buyer.getReserved()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void unaOrdenDeOtroUsuarioNoSeCancelaUS610() {
        MarketOrder sale = sell(6L, SELLER, PLAYER, 5, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(soldOut()), List.of(sale),
                List.of(holding(21L, SELLER, PLAYER, costBasis(10, "10.00"), 5)), user(SELLER, "1000.00"),
                user(USER_A, "1000.00"));

        assertThatThrownBy(() -> book.cancel(sale, USER_A, NOW))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasMessage("La orden no pertenece al usuario.");
        assertThat(sale.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void laOrdenInicialNoLaCancelaNiElSuperusuarioUS610() {
        MarketOrder initial = initialOrder(INITIAL_ID, PLAYER, 87, CREATED);
        PlayerBook book = book(KANE, Optional.of(QUOTE), Optional.of(initial), List.of(), List.of(),
                superuser("13.00"));

        assertThatThrownBy(() -> book.cancel(initial, SUPERUSER_ID, NOW))
                .isInstanceOf(OrderNotOwnedException.class);
        assertThat(initial.pending()).isEqualTo(87);
    }

    @Test
    void conElJugadorInactivoYSinCotizacionTambienSeCancelaFR032() {
        MarketOrder sale = sell(6L, SELLER, PLAYER, 5, CREATED);
        Holding sellerHolding = holding(21L, SELLER, PLAYER, costBasis(10, "10.00"), 5);
        PlayerBook book = book(inactivePlayer(PLAYER, "Harry Kane"), Optional.empty(), Optional.of(soldOut()),
                List.of(sale), List.of(sellerHolding), user(SELLER, "1000.00"));

        BookOutcome outcome = book.cancel(sale, SELLER, NOW);

        assertThat(outcome.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sellerHolding.getCommitted()).isZero();
    }

    /**
     * Juntando las órdenes que había y las del resultado, las abiertas son todas de un mismo lado.
     */
    private static void assertOneSideOpen(List<MarketOrder> before, BookOutcome outcome) {
        List<MarketOrder> all = new ArrayList<>(before);
        all.addAll(outcome.orders());
        assertThat(all.stream().filter(MarketOrder::isOpen).filter(order -> !order.isInitial())
                .map(MarketOrder::getSide).distinct()).hasSizeLessThanOrEqualTo(1);
    }

    private static MarketOrder soldOut() {
        return initialOrder(INITIAL_ID, PLAYER, 0, CREATED);
    }

    private static Quote quoteOf(String value) {
        return quote(QUOTE_ID, PLAYER, value, "0.300000", at("2026-08-24T00:00"));
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
