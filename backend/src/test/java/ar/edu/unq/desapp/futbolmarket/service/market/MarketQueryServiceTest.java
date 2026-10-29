package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.SUPERUSER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.superuser;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidDateRangeException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.AccountAccessDeniedException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.UserNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class MarketQueryServiceTest {
    private static final long SELLER = 7L;
    private static final Player KANE = activePlayer(12L, "Harry Kane");
    private static final long OWNER = 20L;
    private static final long OTHER = 21L;
    private static final long MISSING_USER = 9999L;

    @Mock
    private MarketOrderRepository orderRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private TradeRepository tradeRepository;

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

    @Test
    void elHistorialDeLaCuentaPropiaEsLaHojaDelRepositoryConElMismoFiltro() {
        TradeFilter filter = TradeFilter.of(KANE.id(), OrderSide.SELL, "2026-09-01", "2026-09-10");
        QuotePage<TradeView> page = new QuotePage<>(List.of(), 1, 5, 5);
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "1000.00")));
        when(tradeRepository.findHistory(OWNER, filter, 1, 5)).thenReturn(page);

        assertThat(service.transactions(OWNER, OWNER, filter, 1, 5)).isSameAs(page);
    }

    @Test
    void elAdministradorLeeElHistorialDeCualquierCuenta() {
        QuotePage<TradeView> page = new QuotePage<>(List.of(), 0, 10, 0);
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("0.00")));
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "1000.00")));
        when(tradeRepository.findHistory(OWNER, TradeFilter.none(), 0, 10)).thenReturn(page);

        assertThat(service.transactions(SUPERUSER_ID, OWNER, TradeFilter.none(), 0, 10)).isSameAs(page);
    }

    @Test
    void elHistorialDeOtraCuentaEs403SinBuscarlaNiLeerOperaciones() {
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, "1000.00")));

        assertThatThrownBy(() -> service.transactions(OTHER, OWNER, TradeFilter.none(), 0, 10))
                .isInstanceOf(AccountAccessDeniedException.class);
        verify(userRepository, never()).findById(OWNER);
        verifyNoInteractions(tradeRepository);
    }

    @Test
    void unUsuarioComunRecibe403TambienPorUnaCuentaInexistente() {
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, "1000.00")));

        assertThatThrownBy(() -> service.ordersOf(OTHER, MISSING_USER, 0, 10))
                .isInstanceOf(AccountAccessDeniedException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void elAdministradorRecibe404PorUnaCuentaInexistente() {
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("0.00")));
        when(userRepository.findById(MISSING_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transactions(SUPERUSER_ID, MISSING_USER, TradeFilter.none(), 0, 10))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("No se encontró el usuario con id 9999.");
        verifyNoInteractions(tradeRepository);
    }

    /**
     * El filtro rechaza el rango al armarse, así que el pedido no llega a consultar nada.
     */
    @Test
    void unRangoInvertidoEs400AntesDeConsultarNada() {
        assertThatThrownBy(() -> service.transactions(OWNER, OWNER,
                TradeFilter.of(null, null, "2026-09-10", "2026-09-01"), 0, 10))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("El inicio del rango (2026-09-10) es posterior al final (2026-09-01).");
        verifyNoInteractions(userRepository, tradeRepository);
    }

    @Test
    void lasOrdenesDeLaCuentaPropiaSonLaHojaDelRepository() {
        QuotePage<OrderView> page = new QuotePage<>(List.of(
                new OrderView(sell(2L, OWNER, KANE.id(), 5, at("2026-10-28T10:00")), KANE)), 0, 10, 1);
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "1000.00")));
        when(orderRepository.findOfOwner(OWNER, 0, 10)).thenReturn(page);

        assertThat(service.ordersOf(OWNER, OWNER, 0, 10)).isSameAs(page);
    }

    @Test
    void lasOrdenesDeOtraCuentaSon403() {
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, "1000.00")));

        assertThatThrownBy(() -> service.ordersOf(OTHER, OWNER, 0, 10))
                .isInstanceOf(AccountAccessDeniedException.class);
        verifyNoInteractions(orderRepository);
    }
}
