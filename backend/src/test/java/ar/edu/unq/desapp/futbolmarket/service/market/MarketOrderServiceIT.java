package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.SaleBlockedByInitialOrderException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;

/**
 * Las compras de punta a punta del servicio contra una H2 propia, con el administrador como
 * superusuario y el reloj quieto el 28/10/2026 (US1), y las ventas entre usuarios (US4), con el
 * reloj que avanza y la cotización que cambia. Después de cada escenario se verifican las
 * invariantes del mercado y que los créditos de las cuentas del escenario se conservan.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketOrderServiceIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final String QUOTE_VALUE = "103.00";
    private static final String SCORE = "0.300000";
    private static final String INITIAL_BALANCE = "1000.00";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:market-order-it-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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

    private MarketDatabase database;
    private Team bayern;
    private Player kane;
    private Quote kaneQuote;
    private final List<Long> scenarioAccounts = new ArrayList<>();
    private BigDecimal scenarioCredits;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        database.clean();
        clock.set(NOW);
        bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = tradablePlayer("8300", "Harry Kane");
        kaneQuote = database.quote(kane, QUOTE_VALUE, SCORE, QUOTE_FROM);
        initialOrderService.openMissing();
        scenarioAccounts.clear();
        scenarioAccounts.add(database.superuser().getId());
        scenarioCredits = null;
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        if (scenarioCredits != null) {
            database.assertCreditsConserved(scenarioAccounts, scenarioCredits);
        }
        database.clean();
    }

    @Test
    void comprarDiezContraLaInicialPagaDiezCreditosYDejaNoventaAlSuperusuarioUS14() {
        AppUser buyer = user(INITIAL_BALANCE);
        BigDecimal superuserBefore = database.superuser().getBalance();
        startScenario();

        BookOutcome outcome = service.buy(buyer.getId(), kane.id(), 10);

        assertThat(outcome.order().getId()).isNotNull();
        assertThat(outcome.order().status()).isEqualTo(OrderStatus.EXECUTED);
        AppUser after = database.account(buyer.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("990.00");
        assertThat(after.getReserved()).isEqualByComparingTo("0.00");
        assertThat(database.superuser().getBalance()).isEqualByComparingTo(superuserBefore.add(new BigDecimal("10.00")));
        assertThat(database.holdingOf(buyer, kane)).hasValueSatisfying(holding ->
                assertThat(holding.getQuantity()).isEqualTo(10));
        assertThat(database.initialOrderOf(kane).pending()).isEqualTo(90);
        Trade trade = database.trades().getFirst();
        assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0000");
        assertThat(trade.total()).isEqualByComparingTo("10.00");
        assertThat(trade.sellerType()).isEqualTo(OwnerType.ADMIN);
        assertThat(trade.quoteId()).isEqualTo(kaneQuote.id());
        assertThat(trade.buyOrderId()).isEqualTo(outcome.order().getId());
        assertThat(trade.sellOrderId()).isEqualTo(database.initialOrderOf(kane).getId());
        List<AuditEntry> entries = entriesOf(buyer);
        assertThat(entries).extracting(AuditEntry::eventType)
                .containsExactly(AuditEventType.ORDER_CREATED, AuditEventType.TRADE_EXECUTED);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.actorType()).isEqualTo(AuditActorType.USER);
            assertThat(entry.orderId()).isEqualTo(outcome.order().getId());
        });
        assertThat(entries.get(1).tradeId()).isEqualTo(trade.id());
    }

    @Test
    void conTresEnLaInicialEjecutaTresYDejaSietePendientesConSuReservaUS15() {
        AppUser first = user(INITIAL_BALANCE);
        AppUser buyer = user(INITIAL_BALANCE);
        startScenario();
        service.buy(first.getId(), kane.id(), 97);

        BookOutcome outcome = service.buy(buyer.getId(), kane.id(), 10);

        MarketOrder order = database.ordersOf(buyer).getFirst();
        assertThat(order.getId()).isEqualTo(outcome.order().getId());
        assertThat(order.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(order.getExecuted()).isEqualTo(3);
        assertThat(order.pending()).isEqualTo(7);
        assertThat(order.getReservedCredits()).isEqualByComparingTo("7.21");
        AppUser after = database.account(buyer.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("997.00");
        assertThat(after.getReserved()).isEqualByComparingTo("7.21");
        assertThat(after.availableBalance()).isEqualByComparingTo("989.79");
        assertThat(database.initialOrderOf(kane).status()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void sinSaldoLibreSuficienteRechazaSinCrearNadaUS16() {
        AppUser buyer = user("5.00");
        int entriesBefore = database.auditEntries().size();
        startScenario();

        assertThatThrownBy(() -> service.buy(buyer.getId(), kane.id(), 10))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");

        assertThat(database.ordersOf(buyer)).isEmpty();
        assertThat(database.trades()).isEmpty();
        assertThat(database.auditEntries()).hasSize(entriesBefore);
        assertThat(database.account(buyer.getId()).getBalance()).isEqualByComparingTo("5.00");
        assertThat(database.initialOrderOf(kane).pending()).isEqualTo(100);
    }

    @Test
    void cuatroUsuariosCompranCincoTokensDeCadaUnoDeCincoJugadoresUS111() {
        List<Player> players = new ArrayList<>(List.of(kane));
        Stream.of("Jamal Musiala", "Leroy Sané", "Joshua Kimmich", "Manuel Neuer").forEach(name -> {
            Player player = tradablePlayer("83" + players.size() + "0", name);
            database.quote(player, QUOTE_VALUE, SCORE, QUOTE_FROM);
            players.add(player);
        });
        initialOrderService.openMissing();
        List<AppUser> buyers = Stream.generate(() -> user(INITIAL_BALANCE)).limit(4).toList();
        startScenario();

        buyers.forEach(buyer -> players.forEach(player -> service.buy(buyer.getId(), player.id(), 5)));

        buyers.forEach(buyer -> {
            assertThat(database.account(buyer.getId()).getBalance()).isEqualByComparingTo("975.00");
            players.forEach(player -> assertThat(database.holdingOf(buyer, player)).hasValueSatisfying(holding ->
                    assertThat(holding.getQuantity()).isEqualTo(5)));
        });
        players.forEach(player -> assertThat(database.initialOrderOf(player).pending()).isEqualTo(80));
        assertThat(database.trades()).hasSize(20).allSatisfy(trade ->
                assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0000"));
    }

    @Test
    void dosPedidosDelMismoUsuarioQueJuntosSuperanSuLibreRechazanElSegundo() {
        Player musiala = tradablePlayer("8301", "Jamal Musiala");
        database.quote(musiala, QUOTE_VALUE, SCORE, QUOTE_FROM);
        initialOrderService.openMissing();
        AppUser buyer = user("15.00");
        startScenario();
        service.buy(buyer.getId(), kane.id(), 10);

        assertThatThrownBy(() -> service.buy(buyer.getId(), musiala.id(), 10))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");

        AppUser after = database.account(buyer.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("5.00");
        assertThat(after.availableBalance()).isNotNegative();
        assertThat(database.ordersOf(buyer)).hasSize(1);
    }

    @Test
    void mientrasLaInicialTieneUnTokenNoSeVendeYAlAgotarseLaVentaSeCreaUS41YUS42() {
        AppUser seller = user(INITIAL_BALANCE);
        AppUser last = user(INITIAL_BALANCE);
        startScenario();
        service.buy(seller.getId(), kane.id(), 99);
        int entriesBefore = database.auditEntries().size();

        assertThatThrownBy(() -> service.sell(seller.getId(), kane.id(), 5))
                .isInstanceOf(SaleBlockedByInitialOrderException.class)
                .hasMessage("Todavía no se puede vender: al administrador le queda 1 token de este jugador.");
        assertThat(database.ordersOf(seller)).hasSize(1);
        assertThat(database.auditEntries()).hasSize(entriesBefore);
        database.assertInvariants();

        service.buy(last.getId(), kane.id(), 1);
        BookOutcome outcome = service.sell(seller.getId(), kane.id(), 5);

        MarketOrder sale = database.ordersOf(seller).getLast();
        assertThat(sale.getId()).isEqualTo(outcome.order().getId());
        assertThat(sale.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(sale.pending()).isEqualTo(5);
        assertThat(database.holdingOf(seller, kane)).hasValueSatisfying(holding -> {
            assertThat(holding.getQuantity()).isEqualTo(99);
            assertThat(holding.getCommitted()).isEqualTo(5);
        });
    }

    @Test
    void unaCompraPendienteSeEjecutaContraUnaVentaConLaCotizacionQuePasoDe103A105US43YUS44() {
        AppUser buyer = user(INITIAL_BALANCE);
        AppUser seller = user(INITIAL_BALANCE);
        startScenario();
        service.buy(seller.getId(), kane.id(), 100);

        service.buy(buyer.getId(), kane.id(), 10);

        AppUser waiting = database.account(buyer.getId());
        assertThat(waiting.getReserved()).isEqualByComparingTo("10.30");
        assertThat(waiting.availableBalance()).isEqualByComparingTo("989.70");
        database.assertInvariants();

        Quote risen = requote("105.00");
        BookOutcome outcome = service.sell(seller.getId(), kane.id(), 4);

        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.quantity()).isEqualTo(4);
            assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0500");
            assertThat(trade.total()).isEqualByComparingTo("4.20");
            assertThat(trade.sellerType()).isEqualTo(OwnerType.USER);
            assertThat(trade.quoteId()).isEqualTo(risen.id());
        });
        MarketOrder purchase = database.ordersOf(buyer).getFirst();
        assertThat(purchase.status()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(purchase.pending()).isEqualTo(6);
        assertThat(purchase.getReservedCredits()).isEqualByComparingTo("6.18");
        AppUser after = database.account(buyer.getId());
        assertThat(after.getReserved()).isEqualByComparingTo("6.18");
        assertThat(after.availableBalance()).isEqualByComparingTo("989.62");
        assertThat(database.account(seller.getId()).getBalance()).isEqualByComparingTo("904.20");
        assertThat(outcome.order().status()).isEqualTo(OrderStatus.EXECUTED);
    }

    @Test
    void unaVentaCompletaLaCompraMasViejaYSigueConLaOtraUS46() {
        AppUser fromA = user(INITIAL_BALANCE);
        AppUser fromC = user(INITIAL_BALANCE);
        AppUser seller = user(INITIAL_BALANCE);
        startScenario();
        service.buy(seller.getId(), kane.id(), 100);
        service.buy(fromA.getId(), kane.id(), 5);
        clock.set(NOW.plus(Duration.ofMinutes(1)));
        service.buy(fromC.getId(), kane.id(), 5);
        clock.set(NOW.plus(Duration.ofMinutes(2)));

        BookOutcome outcome = service.sell(seller.getId(), kane.id(), 7);

        assertThat(outcome.trades()).extracting(Trade::buyerId).containsExactly(fromA.getId(), fromC.getId());
        assertThat(outcome.trades()).extracting(Trade::quantity).containsExactly(5, 2);
        assertThat(database.ordersOf(fromA).getFirst().status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(database.ordersOf(fromC).getFirst().pending()).isEqualTo(3);
        assertThat(database.holdingOf(seller, kane)).hasValueSatisfying(holding ->
                assertThat(holding.getQuantity()).isEqualTo(93));
    }

    @Test
    void sinLibreParaElPrecioNuevoSeEjecutanNueveYElRestoSeCancelaPorSaldoUS49() {
        AppUser buyer = user("10.30");
        AppUser seller = user(INITIAL_BALANCE);
        startScenario();
        service.buy(seller.getId(), kane.id(), 100);
        service.buy(buyer.getId(), kane.id(), 10);
        requote("110.00");

        BookOutcome outcome = service.sell(seller.getId(), kane.id(), 10);

        assertThat(outcome.executedQuantity()).isEqualTo(9);
        assertThat(outcome.executedTotal()).isEqualByComparingTo("9.90");
        MarketOrder purchase = database.ordersOf(buyer).getFirst();
        assertThat(purchase.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(purchase.getExecuted()).isEqualTo(9);
        assertThat(purchase.getCancelled()).isEqualTo(1);
        assertThat(purchase.getCancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
        AppUser after = database.account(buyer.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("0.40");
        assertThat(after.getReserved()).isEqualByComparingTo("0.00");
        assertThat(database.ordersOf(seller).getLast().pending()).isEqualTo(1);
        assertThat(database.auditEntries()).filteredOn(entry -> entry.eventType() == AuditEventType.ORDER_CANCELLED)
                .singleElement().satisfies(entry -> {
                    assertThat(entry.actorType()).isEqualTo(AuditActorType.SYSTEM);
                    assertThat(entry.actorId()).isNull();
                    assertThat(entry.orderId()).isEqualTo(purchase.getId());
                    assertThat(entry.cancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
                    assertThat(entry.quantity()).isEqualTo(1);
                });
    }

    @Test
    void unaCotizacionQueRigeDespuesDelPedidoNoSeUsaRigeLaUltimaCompletaUS413() {
        AppUser buyer = user(INITIAL_BALANCE);
        AppUser seller = user(INITIAL_BALANCE);
        startScenario();
        service.buy(seller.getId(), kane.id(), 100);
        service.buy(buyer.getId(), kane.id(), 10);
        database.quote(kane, "105.00", SCORE, NOW.plus(Duration.ofHours(1)));

        BookOutcome outcome = service.sell(seller.getId(), kane.id(), 4);

        assertThat(outcome.quote().id()).isEqualTo(kaneQuote.id());
        assertThat(outcome.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.tokenPrice()).isEqualByComparingTo("1.0300");
            assertThat(trade.total()).isEqualByComparingTo("4.12");
            assertThat(trade.quoteId()).isEqualTo(kaneQuote.id());
        });
    }

    /**
     * Una cotización nueva de Kane que rige desde una hora después de {@link #NOW}, con el reloj ya
     * pasado a esa vigencia.
     */
    private Quote requote(String value) {
        Instant validFrom = NOW.plus(Duration.ofHours(1));
        Quote quote = database.quote(kane, value, SCORE, validFrom);
        clock.set(validFrom.plus(Duration.ofMinutes(1)));
        return quote;
    }

    private Player tradablePlayer(String externalId, String name) {
        return database.quoteDatabase().player(externalId, name, Position.FORWARD, bayern);
    }

    private AppUser user(String balance) {
        AppUser user = database.user(balance);
        scenarioAccounts.add(user.getId());
        return user;
    }

    /**
     * Desde acá se cuentan los créditos de las cuentas del escenario.
     */
    private void startScenario() {
        scenarioCredits = database.totalBalance(scenarioAccounts);
    }

    private List<AuditEntry> entriesOf(AppUser user) {
        return database.auditEntries().stream()
                .filter(entry -> user.getId().equals(entry.actorId()))
                .toList();
    }
}
