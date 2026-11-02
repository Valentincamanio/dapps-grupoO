package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

class MarketOrderFilterTest {

    @Test
    void elEquipoSeNormalizaComoEnElCatalogo() {
        MarketOrderFilter filter = new MarketOrderFilter(OrderSide.SELL, League.PREMIER, "  Arsenal   FC ",
                OwnerType.USER, 7L);

        assertThat(filter.team()).isEqualTo("Arsenal FC");
        assertThat(filter.side()).isEqualTo(OrderSide.SELL);
        assertThat(filter.league()).isEqualTo(League.PREMIER);
        assertThat(filter.ownerType()).isEqualTo(OwnerType.USER);
        assertThat(filter.playerId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void unEquipoVacioSeRechazaConElMensajeDelCatalogo(String team) {
        assertThatThrownBy(() -> new MarketOrderFilter(null, null, team, null, null))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El equipo del filtro no puede estar vacío.");
    }

    @Test
    void sinFiltrosNingunoFiltra() {
        MarketOrderFilter filter = MarketOrderFilter.none();

        assertThat(filter).isEqualTo(new MarketOrderFilter(null, null, null, null, null));
    }
}
