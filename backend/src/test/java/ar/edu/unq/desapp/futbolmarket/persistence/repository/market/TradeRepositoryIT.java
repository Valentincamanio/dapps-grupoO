package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.TradeSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * Las operaciones contra la base, con una H2 propia. La cotización usa la estrategia que siembra
 * {@code StrategySeeder} al arrancar.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:trade-repository-it;DB_CLOSE_DELAY=-1")
class TradeRepositoryIT {
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final List<String> WRITE_PREFIXES = List.of("update", "delete", "remove", "set");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TradeRepository repository;

    @Autowired
    private MarketOrderRepository orderRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private TradeSQLDAO tradeDAO;

    @Autowired
    private MarketOrderSQLDAO orderDAO;

    @Autowired
    private AppUserSQLDAO appUserDAO;

    private QuoteDatabase database;
    private long superuser;
    private long buyer;
    private long seller;
    private Player kane;
    private long quoteId;
    private long initialOrderId;
    private long buyOrderId;
    private long sellOrderId;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        cleanDatabase();
        Team team = database.team("57", "Arsenal FC");
        kane = database.player("8300", "Harry Kane", Position.FORWARD, team);
        superuser = user("administrador", Role.ADMIN, "0.00");
        buyer = user("comprador", Role.USER, "1000.00");
        seller = user("vendedor", Role.USER, "1000.00");
        quoteId = quoteRepository.saveAll(List.of(new Quote(null, kane.id(), WEEK, WEEK.end(), new BigDecimal("103.00"),
                new BigDecimal("100.00"), new BigDecimal("0.300000"), QuoteType.CALCULATED,
                strategyRepository.findCatalog().activeVersion().ref()))).getFirst().id();
        initialOrderId = orderRepository.save(MarketOrder.initialFor(superuser, kane.id(), AT)).getId();
        buyOrderId = orderRepository.save(MarketOrder.buy(buyer, kane.id(), 10, new BigDecimal("10.30"),
                new BigDecimal("1.0300"), AT)).getId();
        sellOrderId = orderRepository.save(MarketOrder.sell(seller, kane.id(), 5, AT)).getId();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void idaYVueltaDeUnaCompraAlSuperusuarioSinGananciaRealizada() {
        Trade trade = new Trade(null, AT, kane.id(), buyOrderId, initialOrderId, buyer, superuser, OwnerType.ADMIN, 10,
                new BigDecimal("1.0000"), new BigDecimal("10.00"), quoteId, new BigDecimal("1000.00"),
                new BigDecimal("990.00"), new BigDecimal("0.00"), new BigDecimal("10.00"), 0, 10, 100, 90, null);

        Trade saved = repository.saveAll(List.of(trade)).getFirst();
        Trade read = repository.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(read).isEqualTo(withId(trade, saved.id()));
    }

    @Test
    void idaYVueltaDeUnaOperacionEntreUsuariosConSuGananciaRealizada() {
        Trade trade = new Trade(null, AT, kane.id(), buyOrderId, sellOrderId, buyer, seller, OwnerType.USER, 5,
                new BigDecimal("1.0300"), new BigDecimal("5.15"), quoteId, new BigDecimal("1000.00"),
                new BigDecimal("994.85"), new BigDecimal("1000.00"), new BigDecimal("1005.15"), 0, 5, 10, 5,
                new BigDecimal("0.15"));

        Trade saved = repository.saveAll(List.of(trade)).getFirst();
        Trade read = repository.findById(saved.id()).orElseThrow();

        assertThat(read).isEqualTo(withId(trade, saved.id()));
        assertThat(read.realizedProfit()).isEqualTo(new BigDecimal("0.15"));
    }

    @Test
    void elRepositoryNoTieneMetodosParaModificarNiBorrarOperaciones() {
        List<String> methods = publicMethodNames(TradeRepository.class);

        assertThat(methods).noneMatch(TradeRepositoryIT::writes);
        assertThat(methods).contains("saveAll");
    }

    @Test
    void laFilaDeUnaOperacionEsInmutableYNoTieneSetters() {
        List<String> methods = publicMethodNames(TradeSQL.class);

        assertThat(methods).noneMatch(name -> name.startsWith("set"));
        assertThat(TradeSQL.class.isAnnotationPresent(Immutable.class)).isTrue();
    }

    @Test
    void laGananciaRealizadaEsLaSumaDeLasVentasDelUsuario() {
        repository.saveAll(List.of(
                sale(seller, OwnerType.USER, new BigDecimal("0.20")),
                sale(seller, OwnerType.USER, new BigDecimal("-0.05")),
                sale(buyer, OwnerType.USER, new BigDecimal("1.00"))));

        BigDecimal realized = repository.realizedProfitOf(seller);

        assertThat(realized).isEqualTo(new BigDecimal("0.15"));
    }

    @Test
    void sinVentasLaGananciaRealizadaEsCero() {
        repository.saveAll(List.of(sale(seller, OwnerType.USER, new BigDecimal("0.20"))));

        BigDecimal realized = repository.realizedProfitOf(buyer);

        assertThat(realized).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void lasVentasDelSuperusuarioNoTienenGananciaRealizada() {
        repository.saveAll(List.of(sale(superuser, OwnerType.ADMIN, null)));

        BigDecimal realized = repository.realizedProfitOf(superuser);

        assertThat(realized).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void elHistorialTraeLasOperacionesDelUsuarioDeLaMasNuevaALaMasViejaConJugadorYCotizacion() {
        History history = seedHistory();

        QuotePage<TradeView> page = repository.findHistory(buyer, TradeFilter.none(), 0, 10);

        assertThat(idsOf(page)).containsExactly(history.saleToSeller(), history.purchaseFromSeller(),
                history.purchaseFromAdmin(), history.salahPurchase());
        TradeView first = page.content().getFirst();
        assertThat(first.player().name()).isEqualTo("Harry Kane");
        assertThat(first.player().team().name()).isEqualTo("Arsenal FC");
        assertThat(first.quote().id()).isEqualTo(quoteId);
        assertThat(first.quote().value()).isEqualTo(new BigDecimal("103.00"));
        assertThat(first.quote().strategy().name()).isNotBlank();
    }

    @Test
    void elTipoSeMiraDesdeElLadoDelUsuario() {
        History history = seedHistory();

        QuotePage<TradeView> purchases = repository.findHistory(buyer, new TradeFilter(null, OrderSide.BUY, null, null),
                0, 10);
        QuotePage<TradeView> sales = repository.findHistory(buyer, new TradeFilter(null, OrderSide.SELL, null, null),
                0, 10);

        assertThat(idsOf(purchases)).containsExactly(history.purchaseFromSeller(), history.purchaseFromAdmin(),
                history.salahPurchase());
        assertThat(idsOf(sales)).containsExactly(history.saleToSeller());
    }

    @Test
    void filtraPorJugador() {
        History history = seedHistory();

        QuotePage<TradeView> page = repository.findHistory(buyer, new TradeFilter(history.salah(), null, null, null),
                0, 10);

        assertThat(idsOf(page)).containsExactly(history.salahPurchase());
    }

    @Test
    void elRangoDeFechasIncluyeLosDosDiasEnHoraDeArgentina() {
        History history = seedHistory();

        QuotePage<TradeView> page = repository.findHistory(buyer,
                TradeFilter.of(null, null, "2026-08-25", "2026-08-26"), 0, 10);

        assertThat(idsOf(page)).containsExactly(history.saleToSeller(), history.purchaseFromSeller());
    }

    @Test
    void elHistorialSeRecorrePorHojas() {
        History history = seedHistory();

        QuotePage<TradeView> second = repository.findHistory(buyer, TradeFilter.none(), 1, 3);

        assertThat(idsOf(second)).containsExactly(history.salahPurchase());
        assertThat(second.totalElements()).isEqualTo(4);
        assertThat(second.hasPrevious()).isTrue();
        assertThat(second.hasNext()).isFalse();
    }

    /**
     * El comprador del escenario compra al superusuario el 24/08, a otro usuario el 25/08 y le vende
     * el 26/08, siempre a las 12 de Argentina; una compra de Salah del 23/08; y una operación del
     * vendedor con el superusuario, que no es del comprador.
     */
    @Test
    void lasOperacionesAnterioresAUnInstanteVanDeLaMasViejaALaMasNuevaSinElInstante() {
        History history = seedHistory();

        List<Trade> before = repository.findOfUserBefore(buyer, AT.plus(Duration.ofDays(1)));

        assertThat(before).extracting(Trade::id).containsExactly(history.salahPurchase(), history.purchaseFromAdmin());
        assertThat(before).allSatisfy(trade -> assertThat(trade.buyerId()).isEqualTo(buyer));
    }

    @Test
    void lasOperacionesDelMismoInstanteVanPorId() {
        long first = save(trade(buyer, superuser, OwnerType.ADMIN, kane.id(), AT));
        long second = save(trade(buyer, superuser, OwnerType.ADMIN, kane.id(), AT));

        assertThat(repository.findOfUserBefore(buyer, AT.plusSeconds(1))).extracting(Trade::id)
                .containsExactly(first, second);
        assertThat(repository.findLastOfUserBefore(buyer, AT.plusSeconds(1))).map(Trade::id).contains(second);
        assertThat(repository.findFirstOfUserAtOrAfter(buyer, AT)).map(Trade::id).contains(first);
    }

    @Test
    void laUltimaAnteriorYLaPrimeraPosteriorAUnInstanteQueEntraEnLaPosterior() {
        History history = seedHistory();
        Instant end = AT.plus(Duration.ofDays(1));

        assertThat(repository.findLastOfUserBefore(buyer, end)).map(Trade::id).contains(history.purchaseFromAdmin());
        assertThat(repository.findFirstOfUserAtOrAfter(buyer, end)).map(Trade::id)
                .contains(history.purchaseFromSeller());
        assertThat(repository.findLastOfUserBefore(seller, end).orElseThrow().buyerId()).isEqualTo(seller);
    }

    @Test
    void sinOperacionesAntesODespuesNoHayNinguna() {
        seedHistory();

        assertThat(repository.findOfUserBefore(buyer, AT.minus(Duration.ofDays(2)))).isEmpty();
        assertThat(repository.findLastOfUserBefore(buyer, AT.minus(Duration.ofDays(2)))).isEmpty();
        assertThat(repository.findFirstOfUserAtOrAfter(buyer, AT.plus(Duration.ofDays(3)))).isEmpty();
    }

    private History seedHistory() {
        Player salah = database.player("8301", "Mohamed Salah", Position.FORWARD,
                database.team("64", "Liverpool FC"));
        long salahPurchase = save(trade(buyer, superuser, OwnerType.ADMIN, salah.id(), AT.minus(Duration.ofDays(1))));
        long fromAdmin = save(trade(buyer, superuser, OwnerType.ADMIN, kane.id(), AT));
        long fromSeller = save(trade(buyer, seller, OwnerType.USER, kane.id(), AT.plus(Duration.ofDays(1))));
        long toSeller = save(trade(seller, buyer, OwnerType.USER, kane.id(), AT.plus(Duration.ofDays(2))));
        save(trade(seller, superuser, OwnerType.ADMIN, kane.id(), AT));
        return new History(salah.id(), salahPurchase, fromAdmin, fromSeller, toSeller);
    }

    private long save(Trade trade) {
        return repository.saveAll(List.of(trade)).getFirst().id();
    }

    private Trade trade(long buyerId, long sellerId, OwnerType sellerType, long playerId, Instant executedAt) {
        long sellOrder = sellerType == OwnerType.ADMIN ? initialOrderId : sellOrderId;
        return new Trade(null, executedAt, playerId, buyOrderId, sellOrder, buyerId, sellerId, sellerType, 1,
                new BigDecimal("1.0300"), new BigDecimal("1.03"), quoteId, new BigDecimal("1000.00"),
                new BigDecimal("998.97"), new BigDecimal("1000.00"), new BigDecimal("1001.03"), 0, 1, 1, 0,
                sellerType == OwnerType.ADMIN ? null : new BigDecimal("0.03"));
    }

    private static List<Long> idsOf(QuotePage<TradeView> page) {
        return page.content().stream().map(view -> view.trade().id()).toList();
    }

    /** Las operaciones del historial de prueba. */
    private record History(long salah, long salahPurchase, long purchaseFromAdmin, long purchaseFromSeller,
                           long saleToSeller) {
    }

    /**
     * Una venta de un token del vendedor al comprador. Las órdenes son las del escenario: la
     * consulta solo mira quién vende.
     */
    private Trade sale(long sellerId, OwnerType sellerType, BigDecimal realizedProfit) {
        long buyerId = sellerId == buyer ? seller : buyer;
        return new Trade(null, AT, kane.id(), buyOrderId, sellOrderId, buyerId, sellerId, sellerType, 1,
                new BigDecimal("1.0300"), new BigDecimal("1.03"), quoteId, new BigDecimal("1000.00"),
                new BigDecimal("998.97"), new BigDecimal("1000.00"), new BigDecimal("1001.03"), 0, 1, 1, 0,
                realizedProfit);
    }

    private static List<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();
    }

    private static boolean writes(String methodName) {
        return WRITE_PREFIXES.stream().anyMatch(methodName::startsWith);
    }

    private static Trade withId(Trade trade, Long id) {
        return new Trade(id, trade.executedAt(), trade.playerId(), trade.buyOrderId(), trade.sellOrderId(),
                trade.buyerId(), trade.sellerId(), trade.sellerType(), trade.quantity(), trade.tokenPrice(),
                trade.total(), trade.quoteId(), trade.buyerBalanceBefore(), trade.buyerBalanceAfter(),
                trade.sellerBalanceBefore(), trade.sellerBalanceAfter(), trade.buyerTokensBefore(),
                trade.buyerTokensAfter(), trade.sellerTokensBefore(), trade.sellerTokensAfter(),
                trade.realizedProfit());
    }

    private long user(String username, Role role, String balance) {
        AppUser user = AppUser.reconstitute(null, username, username + "@correo.com", PASSWORD_HASH, null, role,
                new BigDecimal(balance));
        return appUserRepository.save(user).getId();
    }

    private void cleanDatabase() {
        tradeDAO.deleteAll();
        orderDAO.deleteAll();
        appUserDAO.deleteAll();
        database.clean();
    }
}
