package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;

/**
 * Pedidos simultáneos contra una H2 propia (research D13, US4.12 y el caso de borde de dos pedidos
 * del mismo usuario). Los pedidos arrancan juntos con un {@link CountDownLatch} y solo se verifica
 * el estado final: el orden en que se atienden no es determinista, el resultado sí.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketConcurrencyIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final String INITIAL_BALANCE = "1000.00";
    private static final int BUYERS = 20;
    private static final int LEFT_IN_INITIAL = 10;
    private static final long WAIT_SECONDS = 60;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:market-concurrency-it-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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

    private MarketDatabase database;
    private Player kane;
    private Player musiala;
    private final List<Long> scenarioAccounts = new ArrayList<>();
    private BigDecimal scenarioCredits;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        database.quote(musiala, "103.00", "0.300000", QUOTE_FROM);
        initialOrderService.openMissing();
        scenarioAccounts.clear();
        scenarioAccounts.add(database.superuser().getId());
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.assertCreditsConserved(scenarioAccounts, scenarioCredits);
        database.clean();
    }

    @Test
    void veinteUsuariosPorLosUltimosDiezTokensDejanDiezEjecutadasYDiezPendientesUS412() throws Exception {
        AppUser first = user(INITIAL_BALANCE);
        List<AppUser> buyers = Stream.generate(() -> user(INITIAL_BALANCE)).limit(BUYERS).toList();
        scenarioCredits = database.totalBalance(scenarioAccounts);
        service.buy(first.getId(), kane.id(), 100 - LEFT_IN_INITIAL);

        List<Optional<Exception>> results = all(buyers.stream()
                .<Callable<Object>>map(buyer -> () -> service.buy(buyer.getId(), kane.id(), 1))
                .toList());

        assertThat(results).allSatisfy(result -> assertThat(result).isEmpty());
        List<MarketOrder> orders = buyers.stream().map(buyer -> database.ordersOf(buyer).getFirst()).toList();
        assertThat(orders).filteredOn(order -> order.status() == OrderStatus.EXECUTED).hasSize(LEFT_IN_INITIAL);
        assertThat(orders).filteredOn(order -> order.status() == OrderStatus.PENDING)
                .hasSize(BUYERS - LEFT_IN_INITIAL)
                .allSatisfy(order -> assertThat(order.getReservedCredits()).isEqualByComparingTo("1.03"));
        assertThat(database.initialOrderOf(kane).status()).isEqualTo(OrderStatus.EXECUTED);
        buyers.forEach(buyer -> {
            AppUser after = database.account(buyer.getId());
            assertThat(after.getBalance().subtract(after.getReserved()))
                    .isIn(new BigDecimal("999.00"), new BigDecimal("998.97"));
        });
    }

    @Test
    void dosPedidosSimultaneosDelMismoUsuarioQueJuntosSuperanSuLibreRechazanUno() throws Exception {
        AppUser buyer = user("15.00");
        scenarioCredits = database.totalBalance(scenarioAccounts);

        List<Optional<Exception>> results = all(List.of(
                () -> service.buy(buyer.getId(), kane.id(), 10),
                () -> service.buy(buyer.getId(), musiala.id(), 10)));

        assertThat(results).filteredOn(Optional::isEmpty).hasSize(1);
        assertThat(results).filteredOn(Optional::isPresent).singleElement()
                .satisfies(result -> assertThat(result).containsInstanceOf(InsufficientCreditsException.class));
        AppUser after = database.account(buyer.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("5.00");
        assertThat(after.getReserved()).isEqualByComparingTo("0.00");
        assertThat(database.ordersOf(buyer)).hasSize(1);
    }

    private AppUser user(String balance) {
        AppUser user = database.user(balance);
        scenarioAccounts.add(user.getId());
        return user;
    }

    /**
     * Corre los pedidos a la vez y devuelve, por pedido, la excepción con que terminó o vacío. Solo
     * se acepta un rechazo de negocio (409): cualquier otra excepción, que sería un 500, hace
     * fallar el test.
     */
    private List<Optional<Exception>> all(List<Callable<Object>> requests) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        try {
            List<Future<Object>> futures = requests.stream()
                    .map(request -> executor.submit(() -> {
                        start.await();
                        return request.call();
                    }))
                    .toList();
            start.countDown();
            List<Optional<Exception>> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(outcomeOf(future));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Optional<Exception> outcomeOf(Future<Object> future) throws InterruptedException {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            return Optional.empty();
        } catch (ExecutionException e) {
            assertThat(e.getCause()).as("un pedido terminó con un error que no es un rechazo de negocio")
                    .isInstanceOf(InsufficientCreditsException.class);
            return Optional.of((Exception) e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("un pedido no terminó a tiempo", e);
        }
    }
}
