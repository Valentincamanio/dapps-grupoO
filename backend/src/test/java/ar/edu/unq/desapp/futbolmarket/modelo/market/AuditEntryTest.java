package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class AuditEntryTest {

    private static final long SUPERUSER = 1L;
    private static final long BUYER = 7L;
    private static final long SELLER = 8L;
    private static final long OUTSIDER = 9L;
    private static final long PLAYER = 42L;
    private static final long QUOTE = 300L;
    private static final long TRADE = 900L;
    private static final long BUY_ORDER = 11L;
    private static final long SELL_ORDER = 12L;
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");
    private static final Player KANE = new Player(PLAYER, "ext-42", "Harry Kane", Position.FORWARD,
            new Team(5L, "5", "FC Bayern München", null, League.BUNDESLIGA));

    @Test
    void laOrdenInicialLaRegistraElSuperusuarioConSusCienTokens() {
        MarketOrder initial = MarketOrder.initialFor(SUPERUSER, PLAYER, AT);
        PartyChange change = change(SUPERUSER, "0.00", "0.00", 0, "0.00", "0.00", 100);

        AuditEntry entry = AuditEntry.orderCreated(initial, KANE, AuditActor.superuser(SUPERUSER), change, AT);

        assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CREATED);
        assertThat(entry.occurredAt()).isEqualTo(AT);
        assertThat(entry.actorType()).isEqualTo(AuditActorType.SUPERUSER);
        assertThat(entry.actorId()).isEqualTo(SUPERUSER);
        assertThat(entry.playerId()).isEqualTo(PLAYER);
        assertThat(entry.side()).isEqualTo(OrderSide.SELL);
        assertThat(entry.quantity()).isEqualTo(100);
        assertThat(entry.primary()).isEqualTo(change);
        assertThat(entry.detail()).isEqualTo("Orden inicial de venta de 100 tokens de Harry Kane a 1,0000");
        assertThat(entry.tradeId()).isNull();
        assertThat(entry.tokenPrice()).isNull();
        assertThat(entry.total()).isNull();
        assertThat(entry.quoteId()).isNull();
        assertThat(entry.cancelReason()).isNull();
        assertThat(entry.secondary()).isNull();
    }

    @Test
    void unaCompraCreadaRegistraLoQueReservo() {
        MarketOrder buy = MarketOrder.buy(BUYER, PLAYER, 10, new BigDecimal("10.30"), new BigDecimal("1.0300"), AT);
        PartyChange change = change(BUYER, "1000.00", "0.00", 0, "989.70", "10.30", 0);

        AuditEntry entry = AuditEntry.orderCreated(buy, KANE, AuditActor.user(BUYER), change, AT);

        assertThat(entry.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(entry.actorId()).isEqualTo(BUYER);
        assertThat(entry.side()).isEqualTo(OrderSide.BUY);
        assertThat(entry.quantity()).isEqualTo(10);
        assertThat(entry.detail()).isEqualTo("Orden de compra de 10 tokens de Harry Kane con 10,30 créditos reservados");
    }

    @Test
    void unaVentaCreadaNoReservaCreditos() {
        MarketOrder sell = MarketOrder.sell(SELLER, PLAYER, 5, AT);
        PartyChange change = change(SELLER, "990.00", "0.00", 10, "990.00", "0.00", 10);

        AuditEntry entry = AuditEntry.orderCreated(sell, KANE, AuditActor.user(SELLER), change, AT);

        assertThat(entry.detail()).isEqualTo("Orden de venta de 5 tokens de Harry Kane");
    }

    @Test
    void unaCompraContraLaInicialLlevaAlCompradorComoPrimariaYAlSuperusuarioComoSecundaria() {
        Trade trade = trade(SUPERUSER, OwnerType.ADMIN, 10, "1.0000", "10.00", null);
        PartyChange buyer = change(BUYER, "990.00", "10.00", 0, "990.00", "0.00", 10);
        PartyChange seller = change(SUPERUSER, "0.00", "0.00", 100, "10.00", "0.00", 90);

        AuditEntry entry = AuditEntry.tradeExecuted(trade, KANE, AuditActor.user(BUYER), buyer, seller);

        assertThat(entry.eventType()).isEqualTo(AuditEventType.TRADE_EXECUTED);
        assertThat(entry.occurredAt()).isEqualTo(AT);
        assertThat(entry.actorId()).isEqualTo(BUYER);
        assertThat(entry.orderId()).isEqualTo(BUY_ORDER);
        assertThat(entry.tradeId()).isEqualTo(TRADE);
        assertThat(entry.side()).isEqualTo(OrderSide.BUY);
        assertThat(entry.quantity()).isEqualTo(10);
        assertThat(entry.tokenPrice()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(entry.total()).isEqualTo(new BigDecimal("10.00"));
        assertThat(entry.quoteId()).isEqualTo(QUOTE);
        assertThat(entry.primary()).isEqualTo(buyer);
        assertThat(entry.secondary()).isEqualTo(seller);
        assertThat(entry.cancelReason()).isNull();
        assertThat(entry.detail()).isEqualTo("Compra de 10 tokens de Harry Kane a 1,0000 por 10,00 contra la orden inicial");
    }

    @Test
    void unaVentaQueCruzaUnaCompraPendienteRegistraLaOrdenDelVendedor() {
        Trade trade = trade(SELLER, OwnerType.USER, 5, "1.0300", "5.15", "0.15");
        PartyChange buyer = change(BUYER, "989.70", "10.30", 0, "989.70", "5.15", 5);
        PartyChange seller = change(SELLER, "990.00", "0.00", 10, "995.15", "0.00", 5);

        AuditEntry entry = AuditEntry.tradeExecuted(trade, KANE, AuditActor.user(SELLER), buyer, seller);

        assertThat(entry.orderId()).isEqualTo(SELL_ORDER);
        assertThat(entry.side()).isEqualTo(OrderSide.SELL);
        assertThat(entry.primary()).isEqualTo(buyer);
        assertThat(entry.secondary()).isEqualTo(seller);
        assertThat(entry.detail()).isEqualTo("Compra de 5 tokens de Harry Kane a 1,0300 por 5,15 entre usuarios");
    }

    @Test
    void unaCancelacionDelSistemaNoTieneAutorConId() {
        MarketOrder buy = MarketOrder.buy(BUYER, PLAYER, 10, new BigDecimal("10.30"), new BigDecimal("1.0300"), AT);
        buy.cancelRemainder(CancelReason.INSUFFICIENT_CREDITS);
        PartyChange change = change(BUYER, "0.00", "10.30", 0, "10.30", "0.00", 0);

        AuditEntry entry = AuditEntry.orderCancelled(buy, KANE, AuditActor.system(), change, AT);

        assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CANCELLED);
        assertThat(entry.actorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(entry.actorId()).isNull();
        assertThat(entry.cancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
        assertThat(entry.quantity()).isEqualTo(10);
        assertThat(entry.primary()).isEqualTo(change);
        assertThat(entry.detail())
                .isEqualTo("Cancelación de 10 tokens de la orden de compra de Harry Kane por saldo insuficiente");
        assertThat(entry.tradeId()).isNull();
        assertThat(entry.tokenPrice()).isNull();
        assertThat(entry.total()).isNull();
        assertThat(entry.quoteId()).isNull();
        assertThat(entry.secondary()).isNull();
    }

    @Test
    void unaCancelacionPedidaPorElUsuarioRegistraSoloLoQueQuedabaPendiente() {
        MarketOrder sell = MarketOrder.sell(SELLER, PLAYER, 10, AT);
        sell.fill(3);
        sell.cancelRemainder(CancelReason.USER_REQUEST);
        PartyChange change = change(SELLER, "993.00", "0.00", 7, "993.00", "0.00", 7);

        AuditEntry entry = AuditEntry.orderCancelled(sell, KANE, AuditActor.user(SELLER), change, AT);

        assertThat(entry.quantity()).isEqualTo(7);
        assertThat(entry.cancelReason()).isEqualTo(CancelReason.USER_REQUEST);
        assertThat(entry.detail())
                .isEqualTo("Cancelación de 7 tokens de la orden de venta de Harry Kane a pedido del usuario");
    }

    @Test
    void unaOrdenSinTokensCanceladosNoTieneRegistroDeCancelacion() {
        MarketOrder sell = MarketOrder.sell(SELLER, PLAYER, 10, AT);
        PartyChange change = change(SELLER, "990.00", "0.00", 10, "990.00", "0.00", 10);

        assertThatThrownBy(() -> AuditEntry.orderCancelled(sell, KANE, AuditActor.user(SELLER), change, AT))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void quienNoEsParteDeLaOperacionNoPuedeSerSuAutor() {
        Trade trade = trade(SELLER, OwnerType.USER, 5, "1.0300", "5.15", "0.15");
        PartyChange buyer = change(BUYER, "989.70", "10.30", 0, "989.70", "5.15", 5);
        PartyChange seller = change(SELLER, "990.00", "0.00", 10, "995.15", "0.00", 5);

        assertThatThrownBy(() -> AuditEntry.tradeExecuted(trade, KANE, AuditActor.user(OUTSIDER), buyer, seller))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void laParteDeUnaOrdenTieneQueSerSuDueno() {
        MarketOrder sell = MarketOrder.sell(SELLER, PLAYER, 5, AT);
        PartyChange change = change(BUYER, "990.00", "0.00", 10, "990.00", "0.00", 10);

        assertThatThrownBy(() -> AuditEntry.orderCreated(sell, KANE, AuditActor.user(SELLER), change, AT))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void unUsuarioNoActuaSinId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.USER, null))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void elSistemaNoLlevaId() {
        assertThatThrownBy(() -> new AuditActor(AuditActorType.SYSTEM, BUYER))
                .isInstanceOf(MarketInvariantException.class);
    }

    private static Trade trade(long sellerId, OwnerType sellerType, int quantity, String tokenPrice, String total,
                               String realizedProfit) {
        BigDecimal amount = new BigDecimal(total);
        BigDecimal buyerBalance = new BigDecimal("1000.00");
        BigDecimal sellerBalance = new BigDecimal("990.00");
        return new Trade(TRADE, AT, PLAYER, BUY_ORDER, SELL_ORDER, BUYER, sellerId, sellerType, quantity,
                new BigDecimal(tokenPrice), amount, QUOTE, buyerBalance, buyerBalance.subtract(amount), sellerBalance,
                sellerBalance.add(amount), 0, quantity, 100, 100 - quantity,
                realizedProfit == null ? null : new BigDecimal(realizedProfit));
    }

    private static PartyChange change(long userId, String freeBefore, String reservedBefore, int tokensBefore,
                                      String freeAfter, String reservedAfter, int tokensAfter) {
        return new PartyChange(
                new PartyState(userId, new BigDecimal(freeBefore), new BigDecimal(reservedBefore), tokensBefore),
                new PartyState(userId, new BigDecimal(freeAfter), new BigDecimal(reservedAfter), tokensAfter));
    }
}
