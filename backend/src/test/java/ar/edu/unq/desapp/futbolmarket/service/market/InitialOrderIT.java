package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.ALISSON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.withoutPlayer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.FutbolMarketApplication;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncWriteService;

/**
 * Las órdenes iniciales contra una H2 propia, con el administrador configurado y la generación
 * automática prendida (FR-007 a FR-009, SC-001).
 *
 * <p>Al levantar el contexto el catálogo está vacío, así que el arranque real no crea nada. El
 * arranque se prueba publicando otra vez {@link ApplicationReadyEvent} con jugadores cargados: el
 * listener corre en otro hilo ({@code @Async}) y se espera su resultado con Awaitility, que llega
 * con {@code spring-boot-starter-test} (data-model 12.3). Sin administrador hace falta otro
 * contexto: es la clase anidada.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class InitialOrderIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final Duration STARTUP_WAIT = Duration.ofSeconds(10);
    private static final int REPEATS = 3;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("futbolmarket.market.initial-orders.automatic", () -> "true");
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:initial-order-it-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private InitialOrderService initialOrderService;

    @Autowired
    private SyncWriteService writeService;

    @Autowired
    private PlayerRepository playerRepository;

    private MarketDatabase database;
    private QuoteDatabase catalog;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        catalog = database.quoteDatabase();
        database.clean();
    }

    @AfterEach
    void cleanDatabase() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void despuesDelArranqueCadaJugadorDelCatalogoTieneSuOrdenInicialConSuAuditoria() {
        List<Player> players = threePlayers();

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(FutbolMarketApplication.class),
                new String[0], context, Duration.ZERO));

        await().atMost(STARTUP_WAIT).untilAsserted(() -> assertThat(database.orders()).hasSize(players.size()));
        long superuserId = database.superuser().getId();
        players.forEach(player -> {
            MarketOrder initial = database.initialOrderOf(player);
            assertThat(initial.getSide()).isEqualTo(OrderSide.SELL);
            assertThat(initial.getRequested()).isEqualTo(100);
            assertThat(initial.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(initial.getOwnerId()).isEqualTo(superuserId);
        });
        List<AuditEntry> entries = database.auditEntries();
        assertThat(entries).hasSize(players.size()).allSatisfy(entry -> {
            assertThat(entry.eventType()).isEqualTo(AuditEventType.ORDER_CREATED);
            assertThat(entry.actorType()).isEqualTo(AuditActorType.SUPERUSER);
            assertThat(entry.actorId()).isEqualTo(superuserId);
        });
        assertThat(entries).extracting(AuditEntry::orderId)
                .containsExactlyInAnyOrderElementsOf(database.orders().stream().map(MarketOrder::getId).toList());
    }

    @Test
    void volverAGenerarlasNoCreaNingunaMas() {
        List<Player> players = threePlayers();
        initialOrderService.openMissing();

        for (int repeat = 0; repeat < REPEATS; repeat++) {
            assertThat(initialOrderService.openMissing()).isZero();
        }

        assertThat(database.orders()).hasSize(players.size());
        assertThat(database.auditEntries()).hasSize(players.size());
    }

    @Test
    void sincronizarUnaLigaLeCreaLaOrdenAlJugadorNuevoYNoUnaSegundaALosDemas() {
        LeagueSnapshot premier = snapshot(League.PREMIER);
        LeagueSnapshot withoutAlisson = withoutPlayer(premier, ALISSON_ID);
        writeService.applyLeague(withoutAlisson, SquadAssignment.of(List.of(withoutAlisson)).resolve(List.of()));
        int before = database.orders().size();

        writeService.applyLeague(premier, SquadAssignment.of(List.of(premier)).resolve(List.of()));

        List<Player> catalogPlayers = playerRepository.findAllWithTeam();
        assertThat(before).isEqualTo(catalogPlayers.size() - 1);
        assertThat(database.orders()).hasSize(catalogPlayers.size());
        catalogPlayers.forEach(player -> assertThat(database.initialOrdersOf(player)).hasSize(1));
        Player alisson = playerRepository.findByExternalId(ALISSON_ID).orElseThrow();
        assertThat(database.initialOrderOf(alisson).getCreatedAt()).isNotNull();
    }

    @Test
    void generarlasYSincronizarUnaLigaALaVezNoDuplica() throws Exception {
        threePlayers();
        LeagueSnapshot premier = snapshot(League.PREMIER);
        SquadAssignment assignment = SquadAssignment.of(List.of(premier)).resolve(List.of());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> generation = executor.submit(afterStart(start, initialOrderService::openMissing));
            Future<?> sync = executor.submit(afterStart(start, () -> writeService.applyLeague(premier, assignment)));
            start.countDown();
            generation.get(STARTUP_WAIT.toSeconds(), TimeUnit.SECONDS);
            sync.get(STARTUP_WAIT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<Player> catalogPlayers = playerRepository.findAllWithTeam();
        assertThat(database.orders()).hasSize(catalogPlayers.size());
        catalogPlayers.forEach(player -> assertThat(database.initialOrdersOf(player)).hasSize(1));
    }

    /**
     * Dos jugadores activos y uno inactivo: el inactivo también recibe su orden (research D8).
     */
    private List<Player> threePlayers() {
        Team bayern = catalog.team("5", "FC Bayern München");
        return List.of(
                catalog.player("8300", "Harry Kane", Position.FORWARD, bayern),
                catalog.player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern),
                catalog.inactive(catalog.player("8302", "Thomas Müller", Position.MIDFIELDER, bayern)));
    }

    private static <T> Callable<T> afterStart(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    /**
     * Sin la contraseña del administrador la 001 no crea su cuenta (FR-012): no hay superusuario y
     * ninguna orden inicial se crea. Es otro contexto, con su propia H2 y sin la configuración de la
     * clase de afuera.
     */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "futbolmarket.market.initial-orders.automatic=true",
            "spring.datasource.url=jdbc:h2:mem:initial-order-without-admin-it;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
    class WithoutAdministrator {

        @Autowired
        private ConfigurableApplicationContext nestedContext;

        @Autowired
        private InitialOrderService nestedService;

        @Test
        void sinLaCuentaDelAdministradorNoSeCreaNingunaYQuedaLaAdvertencia(CapturedOutput output) {
            MarketDatabase withoutAdmin = new MarketDatabase(nestedContext);
            withoutAdmin.clean();
            Team bayern = withoutAdmin.quoteDatabase().team("5", "FC Bayern München");
            withoutAdmin.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);

            int created = nestedService.openMissing();

            assertThat(created).isZero();
            assertThat(withoutAdmin.orders()).isEmpty();
            assertThat(withoutAdmin.auditEntries()).isEmpty();
            assertThat(output).contains("No se crean las órdenes iniciales: no existe la cuenta del administrador");
            withoutAdmin.clean();
        }
    }
}
