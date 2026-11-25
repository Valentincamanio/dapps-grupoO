package ar.edu.unq.desapp.futbolmarket.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;

class TradeCounterpartyTest {

    @Test
    void unaVentaDelSuperusuarioEsLaOrdenInicial() {
        TradeCounterparty counterparty = TradeCounterparty.from(OwnerType.ADMIN);

        assertThat(counterparty).isEqualTo(TradeCounterparty.INITIAL_ORDER);
        assertThat(counterparty.tag()).isEqualTo("initial_order");
    }

    @Test
    void unaVentaDeUnUsuarioEsUser() {
        TradeCounterparty counterparty = TradeCounterparty.from(OwnerType.USER);

        assertThat(counterparty).isEqualTo(TradeCounterparty.USER);
        assertThat(counterparty.tag()).isEqualTo("user");
    }

    @Test
    void sinTipoDeVendedorNoHayContraparte() {
        assertThatThrownBy(() -> TradeCounterparty.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Una operación necesita el tipo de vendedor para medirse.");
    }
}
