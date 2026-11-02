package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * La medición de volumen del mercado (SC-009, research D30): el catálogo completo de 2.600
 * jugadores en una H2 en archivo, sus órdenes iniciales y las consultas de un usuario que ya operó,
 * cada una contra el tope de 2 s.
 *
 * <p>Tarda minutos, por eso solo corre con {@code MARKET_BENCHMARK=true} y queda fuera del build
 * normal, como {@code QuoteVolumeBenchmarkIT} (quickstart.md, escenario J). La creación de las
 * órdenes iniciales se registra en el log pero no tiene tope: en producción corre en segundo plano
 * al arrancar.</p>
 *
 * <p>El usuario compra 10 tokens de {@value #PURCHASED_PLAYERS} jugadores distintos antes de medir,
 * así el portfolio y el historial tienen contenido. Las mediciones son una compra más, el portfolio
 * actual y a la fecha de hoy, una hoja del historial y una del listado del mercado.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MARKET_BENCHMARK", matches = "true")
class MarketVolumeIT {
    private static final Logger log = LoggerFactory.getLogger(MarketVolumeIT.class);

    private static final Path DATABASE_DIRECTORY = Path.of("build", "benchmark");
    private static final String DATABASE_NAME = "market-volume";
    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final int TEAMS = 130;
    private static final int PLAYERS_PER_TEAM = 20;
    private static final int PLAYERS = TEAMS * PLAYERS_PER_TEAM;
    private static final int PURCHASED_PLAYERS = 50;
    private static final int TOKENS = 10;
    private static final int PAGE_SIZE = 10;
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");
    private static final String TODAY = "2026-10-28";

    private static final Duration QUERY_LIMIT = Duration.ofSeconds(2);

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:file:./build/benchmark/" + DATABASE_NAME + ";LOCK_TIMEOUT=10000");
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
    private InitialOrderService initialOrderService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private MarketQueryService marketQueryService;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    /**
     * Los archivos de la base de una corrida anterior se borran antes de levantar el contexto, así
     * la carga empieza de cero. Solo los de esta medición: la de cotizaciones usa la misma carpeta.
     */
    @BeforeAll
    static void deletePreviousDatabase() throws IOException {
        if (Files.exists(DATABASE_DIRECTORY)) {
            try (Stream<Path> files = Files.list(DATABASE_DIRECTORY)) {
                files.filter(path -> path.getFileName().toString().startsWith(DATABASE_NAME))
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void elCatalogoCompletoTieneSusOrdenesInicialesYSeOperaDentroDeLosTopesDelSpec() {
        var database = new MarketDatabase(context);
        database.clean();
        long loadStart = System.nanoTime();
        List<Player> players = loadCatalog(database.quoteDatabase());
        log.info("Carga: {} jugadores con su cotización en {}", players.size(),
                Duration.ofNanos(System.nanoTime() - loadStart));

        Timed<Integer> opening = timed(initialOrderService::openMissing);
        log.info("Órdenes iniciales ({}): {}", opening.value(), opening.duration());

        AppUser buyer = database.user("100000.00", REGISTERED_AT);
        long buyerId = buyer.getId();
        players.subList(0, PURCHASED_PLAYERS)
                .forEach(player -> marketOrderService.buy(buyerId, player.id(), TOKENS));
        Player next = players.get(PURCHASED_PLAYERS);

        Timed<BookOutcome> purchase = timed(() -> marketOrderService.buy(buyerId, next.id(), TOKENS));
        Timed<Portfolio> current = timed(() -> portfolioService.current(buyerId, buyerId));
        Timed<Portfolio> atDate = timed(() -> portfolioService.at(buyerId, buyerId, TODAY));
        Timed<QuotePage<TradeView>> history = timed(() -> marketQueryService.transactions(buyerId, buyerId,
                TradeFilter.none(), 0, PAGE_SIZE));
        Timed<QuotePage<OrderView>> listing = timed(() -> marketQueryService.market(MarketOrderFilter.none(), 0,
                PAGE_SIZE));

        log.info("Una compra: {}", purchase.duration());
        log.info("Portfolio actual ({} posiciones): {}", current.value().positions().size(), current.duration());
        log.info("Portfolio a una fecha: {}", atDate.duration());
        log.info("Hoja del historial ({} operaciones): {}", history.value().totalElements(), history.duration());
        log.info("Hoja del listado ({} órdenes abiertas): {}", listing.value().totalElements(), listing.duration());

        assertThat(opening.value()).isEqualTo(PLAYERS);
        assertThat(purchase.value().executedQuantity()).isEqualTo(TOKENS);
        assertThat(current.value().positions()).hasSize(PURCHASED_PLAYERS + 1);
        assertThat(atDate.value().positions()).hasSize(PURCHASED_PLAYERS + 1);
        assertThat(history.value().content()).hasSize(PAGE_SIZE);
        assertThat(history.value().totalElements()).isEqualTo(PURCHASED_PLAYERS + 1L);
        assertThat(listing.value().content()).hasSize(PAGE_SIZE);
        assertThat(listing.value().totalElements()).isEqualTo(PLAYERS);
        database.assertInvariants();

        assertThat(purchase.duration()).isLessThan(QUERY_LIMIT);
        assertThat(current.duration()).isLessThan(QUERY_LIMIT);
        assertThat(atDate.duration()).isLessThan(QUERY_LIMIT);
        assertThat(history.duration()).isLessThan(QUERY_LIMIT);
        assertThat(listing.duration()).isLessThan(QUERY_LIMIT);
    }

    /**
     * 130 equipos de 20 jugadores (las cuatro posiciones repartidas), cada uno con una cotización
     * vigente desde el lunes, guardadas todas juntas.
     */
    private List<Player> loadCatalog(QuoteDatabase database) {
        List<Player> players = new ArrayList<>();
        Position[] positions = Position.values();
        for (int t = 0; t < TEAMS; t++) {
            Team team = database.team(String.valueOf(10_000 + t), "Equipo " + t);
            for (int p = 0; p < PLAYERS_PER_TEAM; p++) {
                int number = t * PLAYERS_PER_TEAM + p;
                players.add(database.player(String.valueOf(100_000 + number), "Jugador " + number,
                        positions[p % positions.length], team));
            }
        }
        var strategy = strategyRepository.findCatalog().activeVersion().ref();
        List<Quote> quotes = players.stream()
                .map(player -> new Quote(null, player.id(), QuoteWeek.containing(QUOTE_FROM), QUOTE_FROM,
                        new BigDecimal("103.00"), Quote.INITIAL_VALUE, new BigDecimal("0.300000"), QuoteType.REQUOTED,
                        strategy))
                .toList();
        quoteRepository.saveAll(quotes);
        return Collections.unmodifiableList(players);
    }

    private static <T> Timed<T> timed(Supplier<T> action) {
        long start = System.nanoTime();
        T value = action.get();
        return new Timed<>(value, Duration.ofNanos(System.nanoTime() - start));
    }

    private record Timed<T>(T value, Duration duration) {
    }
}
