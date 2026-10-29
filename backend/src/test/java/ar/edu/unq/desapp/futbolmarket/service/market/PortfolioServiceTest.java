package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.SUPERUSER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.costBasis;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.holding;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.quote;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.superuser;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.HoldingView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.FutureQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.AccountAccessDeniedException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.DateBeforeRegistrationException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.UserNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    private static final long OWNER = 7L;
    private static final long OTHER = 8L;
    private static final long MISSING_USER = 999L;
    private static final Instant QUOTED_AT = at("2026-10-26T00:00");
    private static final Player KANE = activePlayer(12L, "Harry Kane");
    private static final Player SALAH = activePlayer(13L, "Mohamed Salah");
    private static final String AUGUST_17 = "2026-08-17T10:00";
    private static final Instant SEPTEMBER_1_END = at("2026-09-02T00:00");
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private MarketOrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private PortfolioService service;

    @Test
    void elDuenoVeSuPortfolioConUnaSolaConsultaDeCotizaciones() {
        AppUser owner = user(OWNER, "990.00");
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(owner));
        when(holdingRepository.findPositionsOf(OWNER)).thenReturn(List.of(view(KANE), view(SALAH)));
        when(quoteRepository.findCurrentOf(List.of(KANE.id(), SALAH.id()))).thenReturn(List.of(
                quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT),
                quote(2L, SALAH.id(), "100.00", "0.000000", QUOTED_AT)));
        when(tradeRepository.realizedProfitOf(OWNER)).thenReturn(new BigDecimal("0.20"));

        Portfolio portfolio = service.current(OWNER, OWNER);

        assertThat(portfolio.userId()).isEqualTo(OWNER);
        assertThat(portfolio.positions()).extracting(position -> position.value())
                .containsExactly(new BigDecimal("10.30"), new BigDecimal("10.00"));
        assertThat(portfolio.totals().realizedProfit()).isEqualTo(new BigDecimal("0.20"));
        verify(quoteRepository, times(1)).findCurrentOf(anyCollection());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void unUsuarioComunRecibe403PorUnaCuentaAjenaSinQueSeBusqueLaCuenta() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "1000.00")));

        assertThatThrownBy(() -> service.current(OWNER, OTHER)).isInstanceOf(AccountAccessDeniedException.class);

        verify(userRepository, never()).findById(OTHER);
        verifyNoInteractions(holdingRepository, orderRepository, tradeRepository, quoteRepository);
    }

    @Test
    void unUsuarioComunRecibe403TambienPorUnaCuentaInexistente() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "1000.00")));

        assertThatThrownBy(() -> service.current(OWNER, MISSING_USER))
                .isInstanceOf(AccountAccessDeniedException.class);

        verify(userRepository, never()).findById(MISSING_USER);
    }

    @Test
    void elAdministradorRecibe404PorUnUsuarioInexistente() {
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("0.00")));
        when(userRepository.findById(MISSING_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current(SUPERUSER_ID, MISSING_USER))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("No se encontró el usuario con id 999.");

        verifyNoInteractions(holdingRepository, quoteRepository);
    }

    @Test
    void elAdministradorVeElPortfolioDeOtroUsuario() {
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("0.00")));
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(user(OWNER, "990.00")));
        when(holdingRepository.findPositionsOf(OWNER)).thenReturn(List.of(view(KANE)));
        when(quoteRepository.findCurrentOf(List.of(KANE.id())))
                .thenReturn(List.of(quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT)));
        when(tradeRepository.realizedProfitOf(OWNER)).thenReturn(new BigDecimal("0.00"));

        Portfolio portfolio = service.current(SUPERUSER_ID, OWNER);

        assertThat(portfolio.userId()).isEqualTo(OWNER);
        assertThat(portfolio.positions()).singleElement()
                .satisfies(position -> assertThat(position.invested()).isEqualTo(new BigDecimal("10.00")));
    }

    @Test
    void elDelSuperusuarioSaleDeSusOrdenesIniciales() {
        AppUser superuser = superuser("10.00");
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser));
        when(orderRepository.findInitialOrdersOf(SUPERUSER_ID))
                .thenReturn(List.of(new OrderView(initialOrder(1L, KANE.id(), 90, QUOTED_AT), KANE)));
        when(quoteRepository.findCurrentOf(List.of(KANE.id())))
                .thenReturn(List.of(quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT)));

        Portfolio portfolio = service.current(SUPERUSER_ID, SUPERUSER_ID);

        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(90);
            assertThat(position.averagePrice()).isNull();
        });
        assertThat(portfolio.totals().realizedProfit()).isNull();
        verifyNoInteractions(holdingRepository, tradeRepository);
    }

    @Test
    void unaCredencialDeUnaCuentaQueYaNoExisteEsInvalida() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current(OWNER, OWNER)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(holdingRepository, quoteRepository);
    }

    @Test
    void aUnaFechaReproduceLasOperacionesYValuaConUnaSolaConsultaDeCotizacionesUS72() {
        AppUser owner = registeredUser(OWNER, AUGUST_17);
        Trade purchase = MarketFixtures.purchase(1L, OWNER, KANE.id(), 10, "1.0000", at("2026-08-20T12:00"));
        Trade fromOther = MarketFixtures.sale(2L, OTHER, OWNER, KANE.id(), 5, "1.0300", at("2026-08-26T12:00"));
        givenNow();
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(owner));
        when(tradeRepository.findOfUserBefore(OWNER, SEPTEMBER_1_END)).thenReturn(List.of(purchase, fromOther));
        when(tradeRepository.findLastOfUserBefore(OWNER, SEPTEMBER_1_END)).thenReturn(Optional.of(fromOther));
        when(playerRepository.findIndexedByIds(Set.of(KANE.id()))).thenReturn(Map.of(KANE.id(), KANE));
        when(quoteRepository.findAtOf(Set.of(KANE.id()), SEPTEMBER_1_END)).thenReturn(
                Map.of(KANE.id(), quote(3L, KANE.id(), "101.00", "0.100000", at("2026-08-31T00:00"))));

        Portfolio portfolio = service.at(OWNER, OWNER, "2026-09-01");

        assertThat(portfolio.date()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(15);
            assertThat(position.committed()).isNull();
            assertThat(position.value()).isEqualByComparingTo("15.15");
        });
        assertThat(portfolio.totals().balance()).isEqualByComparingTo("994.85");
        assertThat(portfolio.totals().availableBalance()).isNull();
        assertThat(portfolio.totals().reservedBalance()).isNull();
        verify(quoteRepository, times(1)).findAtOf(anyCollection(), any());
        verify(quoteRepository, never()).findCurrentOf(anyCollection());
        verify(tradeRepository, never()).findFirstOfUserAtOrAfter(anyLong(), any());
        verify(orderRepository, never()).findInitialOrdersCreatedBefore(anyLong(), any());
        verifyNoInteractions(holdingRepository);
    }

    @Test
    void sinOperacionesAnterioresElSaldoEsElPrevioDeLaPrimeraPosterior() {
        givenNow();
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(registeredUser(OWNER, AUGUST_17)));
        when(tradeRepository.findLastOfUserBefore(OWNER, SEPTEMBER_1_END)).thenReturn(Optional.empty());
        when(tradeRepository.findFirstOfUserAtOrAfter(OWNER, SEPTEMBER_1_END)).thenReturn(Optional.of(
                MarketFixtures.purchase(1L, OWNER, KANE.id(), 10, "1.0000", at("2026-09-02T09:00"))));

        Portfolio portfolio = service.at(OWNER, OWNER, "2026-09-01");

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.totals().balance()).isEqualByComparingTo("1000.00");
        assertThat(portfolio.totals().equity()).isEqualByComparingTo("1000.00");
    }

    @Test
    void elPermisoSeVerificaAntesQueLaFecha() {
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, "1000.00")));

        assertThatThrownBy(() -> service.at(OTHER, OWNER, "2026-13-01"))
                .isInstanceOf(AccountAccessDeniedException.class);

        verifyNoInteractions(clock, tradeRepository, quoteRepository, playerRepository);
    }

    @Test
    void unaFechaConFormatoInvalidoEs400ConElValor() {
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(registeredUser(OWNER, AUGUST_17)));

        assertThatThrownBy(() -> service.at(OWNER, OWNER, "2026-13-01"))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessage("La fecha '2026-13-01' no es válida: use el formato AAAA-MM-DD.");

        verifyNoInteractions(tradeRepository, quoteRepository, playerRepository);
    }

    @Test
    void unaFechaFuturaSeRechazaAntesQueUnaAnteriorAlRegistro() {
        givenNow();
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(registeredUser(OWNER, "2026-09-10T12:00")));

        assertThatThrownBy(() -> service.at(OWNER, OWNER, "2026-09-05"))
                .isInstanceOf(FutureQuoteDateException.class)
                .hasMessage("La fecha 2026-09-05 es futura.");

        verifyNoInteractions(tradeRepository, quoteRepository, playerRepository);
    }

    @Test
    void unaFechaAnteriorAlRegistroEs400() {
        givenNow();
        when(userRepository.findById(OWNER)).thenReturn(Optional.of(registeredUser(OWNER, AUGUST_17)));

        assertThatThrownBy(() -> service.at(OWNER, OWNER, "2026-08-16"))
                .isInstanceOf(DateBeforeRegistrationException.class)
                .hasMessage("La fecha 2026-08-16 es anterior al registro del usuario (2026-08-17).");

        verifyNoInteractions(tradeRepository, quoteRepository, playerRepository);
    }

    @Test
    void elDelSuperusuarioAUnaFechaSaleDeSusInicialesCreadasAntesMenosLoVendido() {
        Trade sold = MarketFixtures.purchase(1L, OWNER, KANE.id(), 10, "1.0000", at("2026-08-20T12:00"));
        givenNow();
        when(userRepository.findById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("10.00")));
        when(tradeRepository.findOfUserBefore(SUPERUSER_ID, SEPTEMBER_1_END)).thenReturn(List.of(sold));
        when(orderRepository.findInitialOrdersCreatedBefore(SUPERUSER_ID, SEPTEMBER_1_END))
                .thenReturn(List.of(initialOrder(1L, KANE.id(), 90, at("2026-08-17T00:00"))));
        when(tradeRepository.findLastOfUserBefore(SUPERUSER_ID, SEPTEMBER_1_END)).thenReturn(Optional.of(sold));
        when(playerRepository.findIndexedByIds(Set.of(KANE.id()))).thenReturn(Map.of(KANE.id(), KANE));
        when(quoteRepository.findAtOf(Set.of(KANE.id()), SEPTEMBER_1_END)).thenReturn(
                Map.of(KANE.id(), quote(3L, KANE.id(), "101.00", "0.100000", at("2026-08-31T00:00"))));

        Portfolio portfolio = service.at(SUPERUSER_ID, SUPERUSER_ID, "2026-09-01");

        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(90);
            assertThat(position.averagePrice()).isNull();
            assertThat(position.committed()).isNull();
        });
        assertThat(portfolio.totals().balance()).isEqualByComparingTo("10.00");
        assertThat(portfolio.totals().realizedProfit()).isNull();
        verifyNoInteractions(holdingRepository);
    }

    private void givenNow() {
        when(clock.instant()).thenReturn(at("2026-09-02T12:00"));
    }

    private static AppUser registeredUser(long id, String registeredAt) {
        return AppUser.reconstitute(id, "usuario" + id, "usuario" + id + "@correo.com", PASSWORD_HASH, null, Role.USER,
                new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), at(registeredAt));
    }

    private static HoldingView view(Player player) {
        return new HoldingView(holding(player.id(), OWNER, player.id(), costBasis(10, "10.00"), 0), player);
    }
}
