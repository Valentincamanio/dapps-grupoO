package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

class CostBasisTest {

    @Test
    void dosComprasPromedianLoPagadoSobreLosTokensComprados() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00"));

        CostBasis afterSecondPurchase = basis.acquire(5, new BigDecimal("5.15"));

        assertThat(afterSecondPurchase.quantity()).isEqualTo(15);
        assertThat(afterSecondPurchase.averagePrice()).isEqualTo(new BigDecimal("1.0100"));
        assertThat(afterSecondPurchase.invested()).isEqualTo(new BigDecimal("15.15"));
    }

    @Test
    void venderRealizaLaGananciaSobreElPromedioYNoLoCambia() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00")).acquire(5, new BigDecimal("5.15"));

        Disposal disposal = basis.dispose(5, new BigDecimal("5.25"));

        assertThat(disposal.realizedProfit()).isEqualTo(new BigDecimal("0.20"));
        assertThat(disposal.basis().quantity()).isEqualTo(10);
        assertThat(disposal.basis().averagePrice()).isEqualTo(new BigDecimal("1.0100"));
        assertThat(disposal.basis().invested()).isEqualTo(new BigDecimal("10.10"));
    }

    @Test
    void venderPorDebajoDelPromedioRealizaUnaPerdida() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00"));

        Disposal disposal = basis.dispose(5, new BigDecimal("4.50"));

        assertThat(disposal.realizedProfit()).isEqualTo(new BigDecimal("-0.50"));
    }

    @Test
    void unaCompraDespuesDeVenderPromediaTodasLasComprasDesdeQueLaTenenciaEstuvoEnCero() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00")).acquire(5, new BigDecimal("5.15"))
                .dispose(5, new BigDecimal("5.25")).basis();

        CostBasis afterThirdPurchase = basis.acquire(5, new BigDecimal("5.25"));

        assertThat(afterThirdPurchase.quantity()).isEqualTo(15);
        assertThat(afterThirdPurchase.averagePrice()).isEqualTo(new BigDecimal("1.0200"));
    }

    @Test
    void venderTodoReiniciaLosAcumuladores() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00"));

        Disposal disposal = basis.dispose(10, new BigDecimal("12.00"));

        assertThat(disposal.basis()).isEqualTo(CostBasis.EMPTY);
        assertThat(disposal.realizedProfit()).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void laRecompraDespuesDeVenderTodoArrancaUnPromedioNuevo() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00")).dispose(10, new BigDecimal("12.00")).basis();

        CostBasis repurchased = basis.acquire(5, new BigDecimal("5.50"));

        assertThat(repurchased.averagePrice()).isEqualTo(new BigDecimal("1.1000"));
        assertThat(repurchased.invested()).isEqualTo(new BigDecimal("5.50"));
    }

    @Test
    void sinComprasElPromedioYLoInvertidoSonCero() {
        CostBasis basis = CostBasis.EMPTY;

        BigDecimal average = basis.averagePrice();

        assertThat(average).isEqualTo(new BigDecimal("0.0000"));
        assertThat(basis.invested()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void venderMasDeLoQueTieneEsUnaInvariante() {
        CostBasis basis = CostBasis.EMPTY.acquire(10, new BigDecimal("10.00"));

        assertThatThrownBy(() -> basis.dispose(11, new BigDecimal("11.00")))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("No se pueden vender 11 tokens de una tenencia de 10.");
    }

    @Test
    void comprarCeroTokensEsUnaInvariante() {
        assertThatThrownBy(() -> CostBasis.EMPTY.acquire(0, new BigDecimal("0.00")))
                .isInstanceOf(MarketInvariantException.class);
    }
}
