package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;

/**
 * La auditoría se escribe en la misma transacción que el hecho (FR-025 y US9.6): con una falla
 * forzada en cualquier paso del guardado no queda la orden, ni su operación, ni su registro, ni
 * cambia ningún saldo. Un pedido rechazado no escribe nada.
 *
 * <p>Las fallas se fuerzan con un espía de {@code TradeRepository} y otro de {@code AuditRepository}
 * que, solo en el test que lo pide, lanzan una {@link ForcedFailure} al guardar. En los demás llaman
 * al método real.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditAtomicityIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final String INITIAL_BALANCE = "1000.00";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:audit-atomicity-it-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MarketOrderService service;

    @Autowired
    private InitialOrderService initialOrderService;

    @Autowired
    private MutableClock clock;

    @MockitoSpyBean
    private TradeRepository tradeRepository;

    @MockitoSpyBean
    private AuditRepository auditRepository;

    private MarketDatabase database;
    private Team bayern;
    private Player kane;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        database.clean();
        clock.set(NOW);
        bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void unaFallaAlGuardarLasOperacionesDeUnaCompraNoDejaLaOrdenNiSuAuditoriaNiCambiaSaldos() {
        AppUser buyer = database.user(INITIAL_BALANCE);
        Snapshot before = snapshot(buyer);
        doThrow(new ForcedFailure("Falla forzada al guardar las operaciones."))
                .when(tradeRepository).saveAll(anyCollection());

        assertThatThrownBy(() -> service.buy(buyer.getId(), kane.id(), 10))
                .isInstanceOf(ForcedFailure.class)
                .hasMessage("Falla forzada al guardar las operaciones.");

        assertNothingChanged(buyer, before);
    }

    @Test
    void unaFallaAlGuardarLaAuditoriaDeUnaCompraRevierteLaOrdenLaOperacionYLosSaldos() {
        AppUser buyer = database.user(INITIAL_BALANCE);
        Snapshot before = snapshot(buyer);
        doThrow(new ForcedFailure("Falla forzada al guardar la auditoría."))
                .when(auditRepository).saveAll(anyCollection());

        assertThatThrownBy(() -> service.buy(buyer.getId(), kane.id(), 10))
                .isInstanceOf(ForcedFailure.class)
                .hasMessage("Falla forzada al guardar la auditoría.");

        assertNothingChanged(buyer, before);
    }

    @Test
    void unaCompraQueSeEjecutaGuardaLaOrdenLaOperacionYSusDosRegistrosJuntos() {
        AppUser buyer = database.user(INITIAL_BALANCE);

        service.buy(buyer.getId(), kane.id(), 10);

        MarketOrder purchase = database.ordersOf(buyer).getFirst();
        assertThat(database.trades()).singleElement()
                .satisfies(trade -> assertThat(trade.buyOrderId()).isEqualTo(purchase.getId()));
        assertThat(entriesOfOrder(purchase)).extracting(AuditEntry::eventType)
                .containsExactly(AuditEventType.ORDER_CREATED, AuditEventType.TRADE_EXECUTED);
    }

    @Test
    void unPedidoRechazadoPorSaldoNoDejaNingunaOrdenCreadaUS96() {
        AppUser buyer = database.user("5.00");
        Snapshot before = snapshot(buyer);

        assertThatThrownBy(() -> service.buy(buyer.getId(), kane.id(), 10))
                .isInstanceOf(InsufficientCreditsException.class);

        assertThat(database.auditEntries())
                .noneMatch(entry -> entry.eventType() == AuditEventType.ORDER_CREATED
                        && buyer.getId().equals(entry.actorId()));
        assertNothingChanged(buyer, before);
    }

    @Test
    void unaFallaAlGuardarLaAuditoriaDeLasOrdenesInicialesNoDejaLaOrdenInicial() {
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        List<AuditEntry> auditBefore = database.auditEntries();
        doThrow(new ForcedFailure("Falla forzada al guardar la auditoría."))
                .when(auditRepository).saveAll(anyCollection());

        assertThatThrownBy(() -> initialOrderService.openFor(List.of(musiala.id())))
                .isInstanceOf(ForcedFailure.class)
                .hasMessage("Falla forzada al guardar la auditoría.");

        assertThat(database.initialOrdersOf(musiala)).isEmpty();
        assertThat(database.auditEntries()).isEqualTo(auditBefore);
    }

    @Test
    void despuesDeUnaFallaLaOrdenInicialSeCreaConSuRegistroEnElProximoIntento() {
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        doThrow(new ForcedFailure("Falla forzada al guardar la auditoría."))
                .doCallRealMethod()
                .when(auditRepository).saveAll(anyCollection());
        assertThatThrownBy(() -> initialOrderService.openFor(List.of(musiala.id())))
                .isInstanceOf(ForcedFailure.class);

        int created = initialOrderService.openFor(List.of(musiala.id()));

        assertThat(created).isEqualTo(1);
        MarketOrder initial = database.initialOrderOf(musiala);
        assertThat(entriesOfOrder(initial)).singleElement()
                .satisfies(entry -> assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CREATED));
    }

    private Snapshot snapshot(AppUser buyer) {
        AppUser account = database.account(buyer.getId());
        AppUser superuser = database.superuser();
        return new Snapshot(account.getBalance(), account.getReserved(), superuser.getBalance(),
                database.initialOrderOf(kane).pending(), database.auditEntries(), database.orders().size());
    }

    private void assertNothingChanged(AppUser buyer, Snapshot before) {
        AppUser account = database.account(buyer.getId());
        assertThat(database.ordersOf(buyer)).isEmpty();
        assertThat(database.orders()).hasSize(before.orders());
        assertThat(database.trades()).isEmpty();
        assertThat(database.holdingOf(buyer, kane)).isEmpty();
        assertThat(database.auditEntries()).isEqualTo(before.audit());
        assertThat(account.getBalance()).isEqualByComparingTo(before.balance());
        assertThat(account.getReserved()).isEqualByComparingTo(before.reserved());
        assertThat(database.superuser().getBalance()).isEqualByComparingTo(before.superuserBalance());
        assertThat(database.initialOrderOf(kane).pending()).isEqualTo(before.initialPending());
    }

    private List<AuditEntry> entriesOfOrder(MarketOrder order) {
        return database.auditEntries().stream()
                .filter(entry -> order.getId().equals(entry.orderId()))
                .toList();
    }

    /**
     * Una falla propia del test: no es de persistencia, así que la traducción de excepciones de los
     * repositories la deja pasar tal cual.
     */
    private static final class ForcedFailure extends RuntimeException {
        ForcedFailure(String message) {
            super(message);
        }
    }

    private record Snapshot(BigDecimal balance, BigDecimal reserved, BigDecimal superuserBalance, int initialPending,
                            List<AuditEntry> audit, int orders) {
    }
}
