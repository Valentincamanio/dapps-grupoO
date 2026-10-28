package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

class TradeTest {

    private static final long SUPERUSER = 1L;
    private static final long BUYER = 7L;
    private static final long SELLER = 8L;
    private static final long OUTSIDER = 9L;
    private static final long PLAYER = 42L;
    private static final long QUOTE = 300L;
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");

    @Test
    void elCompradorComproYElVendedorVendio() {
        Trade trade = betweenUsers();

        OrderSide buyerSide = trade.sideFor(BUYER);
        OrderSide sellerSide = trade.sideFor(SELLER);

        assertThat(buyerSide).isEqualTo(OrderSide.BUY);
        assertThat(sellerSide).isEqualTo(OrderSide.SELL);
    }

    @Test
    void quienLeCompraAlSuperusuarioTieneDeContraparteAlAdministrador() {
        Trade trade = againstInitialOrder();

        OwnerType counterparty = trade.counterpartyFor(BUYER);

        assertThat(counterparty).isEqualTo(OwnerType.ADMIN);
    }

    @Test
    void elSuperusuarioTieneDeContraparteAUnUsuario() {
        Trade trade = againstInitialOrder();

        OwnerType counterparty = trade.counterpartyFor(SUPERUSER);

        assertThat(counterparty).isEqualTo(OwnerType.USER);
    }

    @Test
    void entreUsuariosLaContraparteDeCadaUnoEsOtroUsuario() {
        Trade trade = betweenUsers();

        OwnerType buyerCounterparty = trade.counterpartyFor(BUYER);
        OwnerType sellerCounterparty = trade.counterpartyFor(SELLER);

        assertThat(buyerCounterparty).isEqualTo(OwnerType.USER);
        assertThat(sellerCounterparty).isEqualTo(OwnerType.USER);
    }

    @Test
    void quienNoEsParteDeLaOperacionNoTieneLado() {
        Trade trade = betweenUsers();

        assertThatThrownBy(() -> trade.sideFor(OUTSIDER))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void elTotalTieneQueSerElDeLaCantidadAlPrecio() {
        assertThatThrownBy(() -> trade(SELLER, OwnerType.USER, 5, "1.0300", "5.16", "0.15"))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void unaVentaDelSuperusuarioNoTieneGananciaRealizada() {
        assertThatThrownBy(() -> trade(SUPERUSER, OwnerType.ADMIN, 10, "1.0000", "10.00", "0.00"))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void unaVentaDeUnUsuarioTieneGananciaRealizada() {
        assertThatThrownBy(() -> trade(SELLER, OwnerType.USER, 5, "1.0300", "5.15", null))
                .isInstanceOf(MarketInvariantException.class);
    }

    private static Trade againstInitialOrder() {
        return trade(SUPERUSER, OwnerType.ADMIN, 10, "1.0000", "10.00", null);
    }

    private static Trade betweenUsers() {
        return trade(SELLER, OwnerType.USER, 5, "1.0300", "5.15", "0.15");
    }

    private static Trade trade(long sellerId, OwnerType sellerType, int quantity, String tokenPrice, String total,
                               String realizedProfit) {
        BigDecimal amount = new BigDecimal(total);
        BigDecimal buyerBalance = new BigDecimal("1000.00");
        BigDecimal sellerBalance = new BigDecimal("500.00");
        return new Trade(null, AT, PLAYER, 11L, 12L, BUYER, sellerId, sellerType, quantity, new BigDecimal(tokenPrice),
                amount, QUOTE, buyerBalance, buyerBalance.subtract(amount), sellerBalance, sellerBalance.add(amount),
                0, quantity, 100, 100 - quantity, realizedProfit == null ? null : new BigDecimal(realizedProfit));
    }
}
