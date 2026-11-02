package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientTokensException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

class HoldingTest {

    private static final long OWNER = 7L;
    private static final long PLAYER = 42L;

    @Test
    void losTokensLibresSonLaTenenciaMenosLosComprometidos() {
        Holding holding = holdingOf(10);

        holding.commit(6);

        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getCommitted()).isEqualTo(6);
        assertThat(holding.freeTokens()).isEqualTo(4);
    }

    @Test
    void noSeComprometenMasTokensQueLosLibres() {
        Holding holding = holdingOf(10);
        holding.commit(6);

        assertThatThrownBy(() -> holding.commit(5))
                .isInstanceOf(InsufficientTokensException.class)
                .hasMessage("Tokens insuficientes: tiene 4 tokens libres de este jugador.");
        assertThat(holding.getCommitted()).isEqualTo(6);
    }

    @Test
    void conUnSoloTokenLibreElMensajeVaEnSingular() {
        Holding holding = holdingOf(10);
        holding.commit(9);

        assertThatThrownBy(() -> holding.commit(2))
                .isInstanceOf(InsufficientTokensException.class)
                .hasMessage("Tokens insuficientes: tiene 1 token libre de este jugador.");
    }

    @Test
    void unaTenenciaVaciaNoTieneTokensParaVender() {
        Holding holding = Holding.empty(OWNER, PLAYER);

        assertThatThrownBy(() -> holding.commit(1))
                .isInstanceOf(InsufficientTokensException.class)
                .hasMessage("Tokens insuficientes: tiene 0 tokens libres de este jugador.");
    }

    @Test
    void comprarSumaALaTenenciaSinComprometerNada() {
        Holding holding = Holding.empty(OWNER, PLAYER);

        holding.buy(10, new BigDecimal("10.00"));

        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.freeTokens()).isEqualTo(10);
        assertThat(holding.getBasis().averagePrice()).isEqualTo(new BigDecimal("1.0000"));
    }

    @Test
    void venderBajaLaTenenciaYLosComprometidosYDevuelveLaGananciaRealizada() {
        Holding holding = holdingOf(10);
        holding.commit(6);

        BigDecimal realized = holding.sell(6, new BigDecimal("6.60"));

        assertThat(realized).isEqualTo(new BigDecimal("0.60"));
        assertThat(holding.getQuantity()).isEqualTo(4);
        assertThat(holding.getCommitted()).isZero();
        assertThat(holding.freeTokens()).isEqualTo(4);
    }

    @Test
    void cancelarUnaVentaDevuelveSusTokensALosLibres() {
        Holding holding = holdingOf(10);
        holding.commit(6);

        holding.releaseCommitted(6);

        assertThat(holding.getCommitted()).isZero();
        assertThat(holding.freeTokens()).isEqualTo(10);
    }

    @Test
    void noSeLiberanMasTokensQueLosComprometidos() {
        Holding holding = holdingOf(10);
        holding.commit(3);

        assertThatThrownBy(() -> holding.releaseCommitted(4))
                .isInstanceOf(MarketInvariantException.class);
        assertThat(holding.getCommitted()).isEqualTo(3);
    }

    @Test
    void noSeVendenTokensQueNoEstanComprometidos() {
        Holding holding = holdingOf(10);
        holding.commit(3);

        assertThatThrownBy(() -> holding.sell(4, new BigDecimal("4.00")))
                .isInstanceOf(MarketInvariantException.class);
        assertThat(holding.getQuantity()).isEqualTo(10);
    }

    @Test
    void comprometerCeroTokensEsUnaInvariante() {
        Holding holding = holdingOf(10);

        assertThatThrownBy(() -> holding.commit(0))
                .isInstanceOf(MarketInvariantException.class);
    }

    private static Holding holdingOf(int tokens) {
        Holding holding = Holding.empty(OWNER, PLAYER);
        holding.buy(tokens, MarketMath.total(tokens, MarketMath.INITIAL_TOKEN_PRICE));
        return holding;
    }
}
