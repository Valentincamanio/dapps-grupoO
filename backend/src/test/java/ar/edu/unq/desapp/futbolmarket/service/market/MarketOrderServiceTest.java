package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.SUPERUSER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.costBasis;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.holding;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.quote;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.superuser;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientTokensException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class MarketOrderServiceTest {

    private static final long BUYER = 10L;
    private static final long SELLER = 30L;
    private static final long EARLY_BUYER = 20L;
    private static final long LATE_BUYER = 50L;
    private static final long MISSING_PLAYER = 9999L;
    private static final Instant NOW = at("2026-08-24T12:00");
    private static final Instant CREATED = at("2026-08-17T09:00");
    private static final Player KANE = activePlayer(42L, "Harry Kane");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MarketOrderRepository orderRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AuditRepository auditRepository;

    @Captor
    private ArgumentCaptor<Collection<Long>> accountIdsCaptor;

    @Test
    void unJugadorInexistenteEs404SinBloquearNada() {
        when(playerRepository.existsById(MISSING_PLAYER)).thenReturn(false);

        assertThatThrownBy(() -> service().buy(BUYER, MISSING_PLAYER, 10))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador con id 9999.");
        verifyNoInteractions(orderRepository, userRepository, holdingRepository, quoteRepository, tradeRepository,
                auditRepository);
    }

    @Test
    void bloqueaElLibroAntesQueLasCuentasYLasCuentasDeMenorAMayorId() {
        givenBook(user(BUYER, "1000.00"));
        givenSaving();

        service().buy(BUYER, KANE.id(), 5);

        InOrder order = inOrder(orderRepository, userRepository, playerRepository, quoteRepository,
                holdingRepository);
        order.verify(orderRepository).lockBook(KANE.id());
        order.verify(orderRepository).findOpenOfPlayer(KANE.id());
        order.verify(userRepository).lockAllInOrder(accountIdsCaptor.capture());
        order.verify(playerRepository).findById(KANE.id());
        order.verify(quoteRepository).findInForceAt(KANE.id(), NOW);
        order.verify(holdingRepository).findOfPlayer(anyLong(), anyCollection());
        assertThat(accountIdsCaptor.getValue()).containsExactly(SUPERUSER_ID, BUYER, SELLER);
    }

    @Test
    void unRechazoDelModeloNoGuardaNada() {
        givenBook(user(BUYER, "5.00"));

        assertThatThrownBy(() -> service().buy(BUYER, KANE.id(), 10))
                .isInstanceOf(InsufficientCreditsException.class);
        verify(orderRepository, never()).saveAll(anyCollection());
        verify(userRepository, never()).save(any());
        verify(holdingRepository, never()).saveAll(anyCollection());
        verifyNoInteractions(tradeRepository, auditRepository);
    }

    @Test
    void guardaOrdenesCuentasYTenenciasOperacionesYAuditoriaEnEseOrden() {
        givenBook(user(BUYER, "1000.00"));
        givenSaving();

        BookOutcome outcome = service().buy(BUYER, KANE.id(), 5);

        InOrder order = inOrder(orderRepository, userRepository, holdingRepository, tradeRepository, auditRepository);
        order.verify(orderRepository).saveAll(anyCollection());
        order.verify(userRepository, times(3)).save(any());
        order.verify(holdingRepository).saveAll(anyCollection());
        order.verify(tradeRepository).saveAll(anyCollection());
        order.verify(auditRepository).saveAll(anyCollection());
        assertThat(outcome.order().getId()).isNotNull();
        assertThat(outcome.trades()).hasSize(2).allSatisfy(trade -> {
            assertThat(trade.id()).isNotNull();
            assertThat(trade.buyOrderId()).isEqualTo(outcome.order().getId());
        });
        assertThat(outcome.auditEntries()).allSatisfy(entry ->
                assertThat(entry.orderId()).isEqualTo(outcome.order().getId()));
    }

    @Test
    void unaVentaDeUnJugadorInexistenteEs404SinBloquearNada() {
        when(playerRepository.existsById(MISSING_PLAYER)).thenReturn(false);

        assertThatThrownBy(() -> service().sell(SELLER, MISSING_PLAYER, 5))
                .isInstanceOf(PlayerNotFoundException.class);
        verifyNoInteractions(orderRepository, userRepository, holdingRepository, quoteRepository, tradeRepository,
                auditRepository);
    }

    @Test
    void unaVentaBloqueaElLibroYDespuesAlVendedorYALosDuenosDeLasComprasDeMenorAMayorId() {
        givenSellBook(10);
        givenSaving();

        service().sell(SELLER, KANE.id(), 5);

        InOrder order = inOrder(orderRepository, userRepository, playerRepository, quoteRepository,
                holdingRepository);
        order.verify(orderRepository).lockBook(KANE.id());
        order.verify(orderRepository).findOpenOfPlayer(KANE.id());
        order.verify(userRepository).lockAllInOrder(accountIdsCaptor.capture());
        order.verify(playerRepository).findById(KANE.id());
        order.verify(quoteRepository).findInForceAt(KANE.id(), NOW);
        order.verify(holdingRepository).findOfPlayer(anyLong(), anyCollection());
        assertThat(accountIdsCaptor.getValue()).containsExactly(EARLY_BUYER, SELLER, LATE_BUYER);
    }

    @Test
    void unaVentaRechazadaNoGuardaNada() {
        givenSellBook(2);

        assertThatThrownBy(() -> service().sell(SELLER, KANE.id(), 5))
                .isInstanceOf(InsufficientTokensException.class);
        verify(orderRepository, never()).saveAll(anyCollection());
        verify(userRepository, never()).save(any());
        verify(holdingRepository, never()).saveAll(anyCollection());
        verifyNoInteractions(tradeRepository, auditRepository);
    }

    @Test
    void unaVentaGuardaLaVentaYLasComprasQueCruzoConSusOperacionesYAuditoria() {
        givenSellBook(10);
        givenSaving();

        BookOutcome outcome = service().sell(SELLER, KANE.id(), 5);

        InOrder order = inOrder(orderRepository, userRepository, holdingRepository, tradeRepository, auditRepository);
        order.verify(orderRepository).saveAll(anyCollection());
        order.verify(userRepository, times(3)).save(any());
        order.verify(holdingRepository).saveAll(anyCollection());
        order.verify(tradeRepository).saveAll(anyCollection());
        order.verify(auditRepository).saveAll(anyCollection());
        assertThat(outcome.orders()).hasSize(3).allSatisfy(saved -> assertThat(saved.getId()).isNotNull());
        assertThat(outcome.trades()).hasSize(2).allSatisfy(trade ->
                assertThat(trade.sellOrderId()).isEqualTo(outcome.order().getId()));
    }

    /**
     * El libro de Kane con la inicial agotada: el usuario 50 compra 3 y el 20 (más viejo) compra 3.
     * El vendedor tiene {@code tokens} tokens libres.
     */
    private void givenSellBook(int tokens) {
        when(playerRepository.existsById(KANE.id())).thenReturn(true);
        when(orderRepository.lockBook(KANE.id())).thenReturn(Optional.of(initialOrder(1L, KANE.id(), 0, CREATED)));
        when(orderRepository.findOpenOfPlayer(KANE.id())).thenReturn(List.of(
                MarketFixtures.buy(6L, LATE_BUYER, KANE.id(), 3, "1.0300", NOW),
                MarketFixtures.buy(5L, EARLY_BUYER, KANE.id(), 3, "1.0300", CREATED)));
        Map<Long, AppUser> accounts = new LinkedHashMap<>();
        accounts.put(EARLY_BUYER, user(EARLY_BUYER, "1000.00", "3.09"));
        accounts.put(SELLER, user(SELLER, "990.00"));
        accounts.put(LATE_BUYER, user(LATE_BUYER, "1000.00", "3.09"));
        when(userRepository.lockAllInOrder(anyCollection())).thenReturn(accounts);
        when(playerRepository.findById(KANE.id())).thenReturn(Optional.of(KANE));
        when(quoteRepository.findInForceAt(KANE.id(), NOW))
                .thenReturn(Optional.of(quote(300L, KANE.id(), "103.00", "0.300000", CREATED)));
        when(holdingRepository.findOfPlayer(anyLong(), anyCollection()))
                .thenReturn(List.of(holding(20L, SELLER, KANE.id(), costBasis(tokens, "10.00"), 0)));
    }

    /**
     * El libro de Kane: a la inicial le quedan 3 y el usuario 30 vende 5.
     */
    private void givenBook(AppUser buyer) {
        when(playerRepository.existsById(KANE.id())).thenReturn(true);
        when(orderRepository.lockBook(KANE.id())).thenReturn(Optional.of(initialOrder(1L, KANE.id(), 3, CREATED)));
        when(orderRepository.findOpenOfPlayer(KANE.id())).thenReturn(List.of(sell(5L, SELLER, KANE.id(), 5, CREATED)));
        Map<Long, AppUser> accounts = new LinkedHashMap<>();
        accounts.put(SUPERUSER_ID, superuser("0.00"));
        accounts.put(BUYER, buyer);
        accounts.put(SELLER, user(SELLER, "990.00"));
        when(userRepository.lockAllInOrder(anyCollection())).thenReturn(accounts);
        when(playerRepository.findById(KANE.id())).thenReturn(Optional.of(KANE));
        when(quoteRepository.findInForceAt(KANE.id(), NOW))
                .thenReturn(Optional.of(quote(300L, KANE.id(), "103.00", "0.300000", CREATED)));
        when(holdingRepository.findOfPlayer(anyLong(), anyCollection()))
                .thenReturn(List.of(holding(20L, SELLER, KANE.id(), costBasis(10, "10.00"), 5)));
    }

    /**
     * Guardar le pone id a lo que no lo tiene, como la base.
     */
    private void givenSaving() {
        AtomicLong ids = new AtomicLong(1000);
        when(orderRepository.saveAll(anyCollection())).thenAnswer(invocation -> {
            List<MarketOrder> saved = new ArrayList<>();
            for (MarketOrder order : invocation.<Collection<MarketOrder>>getArgument(0)) {
                Long id = order.getId() == null ? ids.incrementAndGet() : order.getId();
                saved.add(MarketOrder.reconstitute(new MarketOrder.State(id, order.getSide(), order.getOwnerId(),
                        order.getPlayerId(), order.getRequested(), order.getExecuted(), order.getCancelled(),
                        order.getReservedCredits(), order.getReservationPrice(), order.getCreatedAt(),
                        order.isInitial(), order.getCancelReason())));
            }
            return saved;
        });
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(holdingRepository.saveAll(anyCollection()))
                .thenAnswer(invocation -> List.copyOf(invocation.<Collection<?>>getArgument(0)));
        when(tradeRepository.saveAll(anyCollection())).thenAnswer(invocation -> {
            List<Trade> saved = new ArrayList<>();
            for (Trade trade : invocation.<Collection<Trade>>getArgument(0)) {
                saved.add(new Trade(ids.incrementAndGet(), trade.executedAt(), trade.playerId(), trade.buyOrderId(),
                        trade.sellOrderId(), trade.buyerId(), trade.sellerId(), trade.sellerType(), trade.quantity(),
                        trade.tokenPrice(), trade.total(), trade.quoteId(), trade.buyerBalanceBefore(),
                        trade.buyerBalanceAfter(), trade.sellerBalanceBefore(), trade.sellerBalanceAfter(),
                        trade.buyerTokensBefore(), trade.buyerTokensAfter(), trade.sellerTokensBefore(),
                        trade.sellerTokensAfter(), trade.realizedProfit()));
            }
            return saved;
        });
        when(auditRepository.saveAll(anyCollection()))
                .thenAnswer(invocation -> List.copyOf(invocation.<Collection<?>>getArgument(0)));
    }

    private MarketOrderService service() {
        PlayerBookLoader loader = new PlayerBookLoader(orderRepository, userRepository, holdingRepository,
                playerRepository, quoteRepository);
        return new MarketOrderService(Clock.fixed(NOW, ZoneOffset.UTC), playerRepository, loader, orderRepository,
                userRepository, holdingRepository, tradeRepository, auditRepository);
    }
}
