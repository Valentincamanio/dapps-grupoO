package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;

@ExtendWith(MockitoExtension.class)
class MarketQueryServiceTest {
    private static final long SELLER = 7L;
    private static final Player KANE = activePlayer(12L, "Harry Kane");

    @Mock
    private MarketOrderRepository orderRepository;

    @InjectMocks
    private MarketQueryService service;

    @Test
    void elListadoEsLaHojaDelRepositoryConElMismoFiltroYLaMismaPaginacion() {
        MarketOrderFilter filter = new MarketOrderFilter(OrderSide.SELL, League.PREMIER, "Arsenal FC", OwnerType.USER,
                KANE.id());
        QuotePage<OrderView> page = new QuotePage<>(List.of(
                new OrderView(initialOrder(1L, KANE.id(), 87, at("2026-10-28T09:00")), KANE),
                new OrderView(sell(2L, SELLER, KANE.id(), 5, at("2026-10-28T10:00")), KANE)), 1, 2, 4);
        when(orderRepository.findMarket(filter, 1, 2)).thenReturn(page);

        QuotePage<OrderView> listed = service.market(filter, 1, 2);

        assertThat(listed).isSameAs(page);
        verify(orderRepository).findMarket(filter, 1, 2);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void sinOrdenesAbiertasLaHojaVieneVacia() {
        QuotePage<OrderView> empty = new QuotePage<>(List.of(), 0, 10, 0);
        when(orderRepository.findMarket(MarketOrderFilter.none(), 0, 10)).thenReturn(empty);

        QuotePage<OrderView> listed = service.market(MarketOrderFilter.none(), 0, 10);

        assertThat(listed.content()).isEmpty();
        assertThat(listed.totalElements()).isZero();
        assertThat(listed.hasNext()).isFalse();
    }
}
