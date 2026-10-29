package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotCancellableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotOwnedException;

class MarketOrderTest {

    private static final long SUPERUSER = 1L;
    private static final long OWNER = 7L;
    private static final long OTHER_USER = 8L;
    private static final long PLAYER = 42L;
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");
    private static final BigDecimal RESERVATION_PRICE = new BigDecimal("1.0300");
    private static final BigDecimal RESERVATION = new BigDecimal("10.30");

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void unaCompraConCantidadFueraDeUnoACienEsUnaInvariante(int quantity) {
        assertThatThrownBy(() -> MarketOrder.buy(OWNER, PLAYER, quantity, RESERVATION, RESERVATION_PRICE, AT))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("La cantidad de una orden tiene que estar entre 1 y 100: " + quantity + ".");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void unaVentaConCantidadFueraDeUnoACienEsUnaInvariante(int quantity) {
        assertThatThrownBy(() -> MarketOrder.sell(OWNER, PLAYER, quantity, AT))
                .isInstanceOf(MarketInvariantException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void aceptaLasCantidadesDeLosBordes(int quantity) {
        assertThatCode(() -> MarketOrder.sell(OWNER, PLAYER, quantity, AT)).doesNotThrowAnyException();
    }

    @Test
    void laOrdenInicialEsUnaVentaPendienteDeCienTokensDelAdministrador() {
        MarketOrder initial = MarketOrder.initialFor(SUPERUSER, PLAYER, AT);

        assertThat(initial.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(initial.getOwnerId()).isEqualTo(SUPERUSER);
        assertThat(initial.getRequested()).isEqualTo(100);
        assertThat(initial.pending()).isEqualTo(100);
        assertThat(initial.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(initial.isInitial()).isTrue();
        assertThat(initial.ownerType()).isEqualTo(OwnerType.ADMIN);
        assertThat(initial.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
        assertThat(initial.getReservationPrice()).isNull();
    }

    @Test
    void lasOrdenesDeLosUsuariosSonDeTipoUsuario() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        assertThat(buy.ownerType()).isEqualTo(OwnerType.USER);
        assertThat(sell.ownerType()).isEqualTo(OwnerType.USER);
        assertThat(buy.isInitial()).isFalse();
    }

    @Test
    void ejecutarParteDeUnaPendienteLaDejaParcial() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        sell.fill(4);

        assertThat(sell.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(sell.getExecuted()).isEqualTo(4);
        assertThat(sell.pending()).isEqualTo(6);
        assertThat(sell.isOpen()).isTrue();
    }

    @Test
    void ejecutarElRestoDeUnaParcialLaDejaEjecutada() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);
        sell.fill(4);

        sell.fill(6);

        assertThat(sell.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(sell.pending()).isZero();
        assertThat(sell.isOpen()).isFalse();
    }

    @Test
    void ejecutarDeUnaVezTodaUnaPendienteLaDejaEjecutada() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        sell.fill(10);

        assertThat(sell.status()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void ejecutarMasQueElPendienteEsUnaInvariante() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);
        sell.fill(4);

        assertThatThrownBy(() -> sell.fill(7))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("No se pueden ejecutar 7 tokens de una orden con 6 pendientes.");
    }

    @Test
    void cancelarUnaPendienteLaDejaCanceladaSinParteEjecutada() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        BigDecimal released = sell.cancelRemainder(CancelReason.USER_REQUEST);

        assertThat(sell.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sell.getCancelled()).isEqualTo(10);
        assertThat(sell.getExecuted()).isZero();
        assertThat(sell.getCancelReason()).isEqualTo(CancelReason.USER_REQUEST);
        assertThat(released).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void cancelarUnaCompraParcialConservaLoEjecutadoYLiberaLaReservaDelRemanente() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        buy.fill(3);
        buy.consumeReservation(new BigDecimal("3.09"));

        BigDecimal released = buy.cancelRemainder(CancelReason.USER_REQUEST);

        assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(buy.getExecuted()).isEqualTo(3);
        assertThat(buy.getCancelled()).isEqualTo(7);
        assertThat(released).isEqualTo(new BigDecimal("7.21"));
        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void unaOrdenEjecutadaNoSeCancela() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);
        sell.fill(10);

        assertThatThrownBy(() -> sell.cancelRemainder(CancelReason.USER_REQUEST))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("La orden ya está ejecutada.");
    }

    @Test
    void unaOrdenCanceladaNoSeVuelveACancelar() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);
        sell.cancelRemainder(CancelReason.USER_REQUEST);

        assertThatThrownBy(() -> sell.cancelRemainder(CancelReason.USER_REQUEST))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("La orden ya está cancelada.");
    }

    @Test
    void laOrdenInicialNuncaSeCancela() {
        MarketOrder initial = MarketOrder.initialFor(SUPERUSER, PLAYER, AT);

        assertThatThrownBy(() -> initial.cancelRemainder(CancelReason.USER_REQUEST))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void elDuenoPuedeOperarSobreSuOrden() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        assertThatCode(() -> sell.requireOwnedBy(OWNER)).doesNotThrowAnyException();
    }

    @Test
    void otroUsuarioNoPuedeOperarSobreUnaOrdenAjena() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        assertThatThrownBy(() -> sell.requireOwnedBy(OTHER_USER))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasMessage("La orden no pertenece al usuario.");
    }

    @Test
    void nadiePuedeOperarSobreUnaOrdenInicialNiSiquieraSuDueno() {
        MarketOrder initial = MarketOrder.initialFor(SUPERUSER, PLAYER, AT);

        assertThatThrownBy(() -> initial.requireOwnedBy(SUPERUSER))
                .isInstanceOf(OrderNotOwnedException.class);
    }

    @Test
    void conDisponibleSuficienteLaReservaVuelveAlRemanenteAlPrecioDeReserva() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        buy.fill(4);
        buy.consumeReservation(new BigDecimal("4.20"));

        BigDecimal change = buy.adjustReservation(new BigDecimal("995.80"));

        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("6.18"));
        assertThat(change).isEqualTo(new BigDecimal("0.08"));
    }

    @Test
    void siNoAlcanzaParaLaReservaCompletaQuedaConLoDisponibleSinCancelarNada() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        buy.fill(4);
        buy.consumeReservation(new BigDecimal("4.40"));

        BigDecimal change = buy.adjustReservation(new BigDecimal("5.90"));

        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("5.90"));
        assertThat(change).isEqualTo(new BigDecimal("0.00"));
        assertThat(buy.pending()).isEqualTo(6);
        assertThat(buy.getCancelled()).isZero();
    }

    @Test
    void unaCompraQueSeEjecutaMasBarataQueSuReservaDevuelveLaDiferencia() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        buy.fill(10);
        buy.consumeReservation(new BigDecimal("10.00"));

        BigDecimal change = buy.adjustReservation(new BigDecimal("990.30"));

        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
        assertThat(change).isEqualTo(new BigDecimal("-0.30"));
    }

    @Test
    void pagarMasQueLaReservaEsUnaInvariante() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);

        assertThatThrownBy(() -> buy.consumeReservation(new BigDecimal("10.31")))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void unaVentaNoReservaCreditos() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        assertThatThrownBy(() -> sell.adjustReservation(new BigDecimal("10.00")))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("Solo una compra reserva créditos.");
    }

    @Test
    void cancelarMasAllaDeLoQueAlcanzaDejaPendienteSoloLoQueSePuedePagar() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);

        buy.cancelBeyond(9);

        assertThat(buy.pending()).isEqualTo(9);
        assertThat(buy.getCancelled()).isEqualTo(1);
        assertThat(buy.getCancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
        assertThat(buy.getReservedCredits()).isEqualTo(RESERVATION);
    }

    @Test
    void nueveEjecutadosYUnoCanceladoLiberanLosCuarentaCentavosQueSobran() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);
        buy.cancelBeyond(9);
        buy.fill(9);
        buy.consumeReservation(new BigDecimal("9.90"));

        BigDecimal change = buy.adjustReservation(new BigDecimal("0.40"));

        assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(buy.getExecuted()).isEqualTo(9);
        assertThat(buy.getCancelled()).isEqualTo(1);
        assertThat(change).isEqualTo(new BigDecimal("-0.40"));
        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 11})
    void cancelarMasAllaConservaAlMenosUnoYMenosQueElPendiente(int keep) {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);

        assertThatThrownBy(() -> buy.cancelBeyond(keep))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void sumarALaReservaCubreUnaOperacionMasCaraQueLoReservado() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 1, new BigDecimal("1.03"), RESERVATION_PRICE, AT);

        buy.addToReservation(new BigDecimal("0.07"));
        buy.consumeReservation(new BigDecimal("1.10"));

        assertThat(buy.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void sumarUnMontoNegativoALaReservaEsUnaInvariante() {
        MarketOrder buy = MarketOrder.buy(OWNER, PLAYER, 10, RESERVATION, RESERVATION_PRICE, AT);

        assertThatThrownBy(() -> buy.addToReservation(new BigDecimal("-0.01")))
                .isInstanceOf(MarketInvariantException.class);
        assertThat(buy.getReservedCredits()).isEqualTo(RESERVATION);
    }

    @Test
    void unaVentaNoSumaAUnaReserva() {
        MarketOrder sell = MarketOrder.sell(OWNER, PLAYER, 10, AT);

        assertThatThrownBy(() -> sell.addToReservation(new BigDecimal("1.00")))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("Solo una compra reserva créditos.");
    }
}
