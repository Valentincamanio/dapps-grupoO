package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.SUPERUSER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.superuser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.config.MarketProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InitialOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Player KANE = activePlayer(42L, "Harry Kane");
    private static final Player MUSIALA = activePlayer(43L, "Jamal Musiala");

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private MarketOrderRepository orderRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AuditRepository auditRepository;

    @Captor
    private ArgumentCaptor<Collection<MarketOrder>> ordersCaptor;

    @Captor
    private ArgumentCaptor<Collection<AuditEntry>> entriesCaptor;

    @Test
    void sinSuperusuarioNoCreaNadaYLoAdvierte(CapturedOutput output) {
        when(userRepository.findSuperuserId()).thenReturn(Optional.empty());

        int created = service(true).openMissing();

        assertThat(created).isZero();
        verify(userRepository, never()).lockById(any());
        verifyNoInteractions(orderRepository, playerRepository, auditRepository);
        assertThat(output).contains("No se crean las órdenes iniciales: no existe la cuenta del administrador");
    }

    @Test
    void bloqueaLaCuentaDelSuperusuarioAntesDeBuscarLosJugadoresSinOrden() {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrder()).thenReturn(List.of(KANE.id()));
        givenPlayersAndSaving(KANE);

        service(true).openMissing();

        InOrder order = inOrder(userRepository, orderRepository);
        order.verify(userRepository).findSuperuserId();
        order.verify(userRepository).lockById(SUPERUSER_ID);
        order.verify(orderRepository).playerIdsWithoutInitialOrder();
    }

    @Test
    void sinJugadoresPendientesNoGuardaNada() {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrder()).thenReturn(List.of());

        int created = service(true).openMissing();

        assertThat(created).isZero();
        verify(orderRepository, never()).saveAll(anyCollection());
        verifyNoInteractions(playerRepository, auditRepository);
    }

    @Test
    void creaUnaVentaInicialPorJugadorConSuAuditoriaDelSuperusuario(CapturedOutput output) {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrder()).thenReturn(List.of(MUSIALA.id(), KANE.id()));
        givenPlayersAndSaving(KANE, MUSIALA);

        int created = service(true).openMissing();

        assertThat(created).isEqualTo(2);
        verify(orderRepository).saveAll(ordersCaptor.capture());
        assertThat(ordersCaptor.getValue()).allSatisfy(order -> {
            assertThat(order.isInitial()).isTrue();
            assertThat(order.getOwnerId()).isEqualTo(SUPERUSER_ID);
            assertThat(order.getRequested()).isEqualTo(100);
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getCreatedAt()).isEqualTo(NOW);
        }).extracting(MarketOrder::getPlayerId).containsExactly(KANE.id(), MUSIALA.id());
        verify(auditRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).allSatisfy(entry -> {
            assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CREATED);
            assertThat(entry.actorType()).isEqualTo(AuditActorType.SUPERUSER);
            assertThat(entry.actorId()).isEqualTo(SUPERUSER_ID);
            assertThat(entry.orderId()).isNotNull();
            assertThat(entry.primary().before().tokens()).isZero();
            assertThat(entry.primary().after().tokens()).isEqualTo(100);
        }).extracting(AuditEntry::detail).containsExactly(
                "Orden inicial de venta de 100 tokens de Harry Kane a 1,0000",
                "Orden inicial de venta de 100 tokens de Jamal Musiala a 1,0000");
        assertThat(output).contains("Se crearon 2 órdenes iniciales del superusuario.");
    }

    @Test
    void openForBuscaSoloEntreEsosJugadores() {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrderIn(List.of(KANE.id()))).thenReturn(List.of(KANE.id()));
        givenPlayersAndSaving(KANE);

        int created = service(true).openFor(List.of(KANE.id()));

        assertThat(created).isOne();
        verify(orderRepository, never()).playerIdsWithoutInitialOrder();
    }

    @Test
    void conLaGeneracionAutomaticaApagadaElArranqueYLaSincronizacionNoTocanNada() {
        InitialOrderService service = service(false);

        int atStartup = service.onStartup();
        int afterSync = service.afterSync(List.of(KANE.id()));

        assertThat(atStartup).isZero();
        assertThat(afterSync).isZero();
        verifyNoInteractions(userRepository, orderRepository, playerRepository, auditRepository);
    }

    @Test
    void conLaGeneracionAutomaticaPrendidaElArranqueCreaLasQueFaltan() {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrder()).thenReturn(List.of(KANE.id()));
        givenPlayersAndSaving(KANE);

        int created = service(true).onStartup();

        assertThat(created).isOne();
    }

    @Test
    void conLaGeneracionAutomaticaPrendidaLaSincronizacionCreaLasDeSusJugadores() {
        givenSuperuser();
        when(orderRepository.playerIdsWithoutInitialOrderIn(List.of(KANE.id(), MUSIALA.id())))
                .thenReturn(List.of(MUSIALA.id()));
        givenPlayersAndSaving(MUSIALA);

        int created = service(true).afterSync(List.of(KANE.id(), MUSIALA.id()));

        assertThat(created).isOne();
    }

    private void givenSuperuser() {
        when(userRepository.findSuperuserId()).thenReturn(Optional.of(SUPERUSER_ID));
        when(userRepository.lockById(SUPERUSER_ID)).thenReturn(Optional.of(superuser("25.00")));
    }

    /**
     * Los jugadores existen y guardar una orden le pone un id nuevo.
     */
    private void givenPlayersAndSaving(Player... players) {
        when(playerRepository.findAllByIds(anyCollection())).thenReturn(List.of(players));
        AtomicLong ids = new AtomicLong(100);
        when(orderRepository.saveAll(anyCollection())).thenAnswer(invocation -> {
            List<MarketOrder> saved = new ArrayList<>();
            for (MarketOrder order : invocation.<Collection<MarketOrder>>getArgument(0)) {
                saved.add(MarketOrder.reconstitute(new MarketOrder.State(ids.incrementAndGet(), order.getSide(),
                        order.getOwnerId(), order.getPlayerId(), order.getRequested(), order.getExecuted(),
                        order.getCancelled(), order.getReservedCredits(), order.getReservationPrice(),
                        order.getCreatedAt(), order.isInitial(), order.getCancelReason())));
            }
            return saved;
        });
    }

    private InitialOrderService service(boolean automatic) {
        return new InitialOrderService(Clock.fixed(NOW, ZoneOffset.UTC),
                new MarketProperties(new MarketProperties.InitialOrders(automatic)), userRepository, orderRepository,
                playerRepository, auditRepository);
    }
}
