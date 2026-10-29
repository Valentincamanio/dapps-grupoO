package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * Las órdenes contra la base, con una H2 propia.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:market-order-repository-it;DB_CLOSE_DELAY=-1")
class MarketOrderRepositoryIT {
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final BigDecimal RESERVATION_PRICE = new BigDecimal("1.0300");
    private static final String INITIAL_INDEX = "ux_market_orders_initial_player";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MarketOrderRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MarketOrderSQLDAO orderDAO;

    @Autowired
    private AppUserSQLDAO appUserDAO;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private QuoteDatabase database;
    private long superuser;
    private long buyer;
    private long seller;
    private Player kane;
    private Player salah;
    private Player son;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        cleanDatabase();
        Team team = database.team("57", "Arsenal FC");
        kane = database.player("8300", "Harry Kane", Position.FORWARD, team);
        salah = database.player("8301", "Mohamed Salah", Position.FORWARD, team);
        son = database.player("8302", "Son Heung-min", Position.FORWARD, team);
        superuser = user("administrador", Role.ADMIN, "0.00");
        buyer = user("comprador", Role.USER, "1000.00");
        seller = user("vendedor", Role.USER, "1000.00");
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void idaYVueltaDeUnaCompraConservaCantidadesReservaYPrecio() {
        MarketOrder buy = MarketOrder.buy(buyer, kane.id(), 10, new BigDecimal("10.30"), RESERVATION_PRICE, AT);
        buy.fill(3);
        buy.consumeReservation(new BigDecimal("3.09"));

        MarketOrder saved = repository.save(buy);
        MarketOrder read = repository.findById(saved.getId()).orElseThrow();

        assertThat(read.getId()).isNotNull();
        assertThat(read.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(read.getOwnerId()).isEqualTo(buyer);
        assertThat(read.getPlayerId()).isEqualTo(kane.id());
        assertThat(read.getRequested()).isEqualTo(10);
        assertThat(read.getExecuted()).isEqualTo(3);
        assertThat(read.getCancelled()).isZero();
        assertThat(read.getReservedCredits()).isEqualTo(new BigDecimal("7.21"));
        assertThat(read.getReservationPrice()).isEqualTo(RESERVATION_PRICE);
        assertThat(read.getCreatedAt()).isEqualTo(AT);
        assertThat(read.isInitial()).isFalse();
        assertThat(read.getCancelReason()).isNull();
        assertThat(read.status()).isEqualTo(OrderStatus.PARTIAL);
    }

    @Test
    void idaYVueltaDeUnaVentaCanceladaConservaElMotivo() {
        MarketOrder sell = MarketOrder.sell(seller, kane.id(), 10, AT);
        sell.fill(4);
        sell.cancelRemainder(CancelReason.USER_REQUEST);

        MarketOrder saved = repository.save(sell);
        MarketOrder read = repository.findById(saved.getId()).orElseThrow();

        assertThat(read.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(read.getExecuted()).isEqualTo(4);
        assertThat(read.getCancelled()).isEqualTo(6);
        assertThat(read.getCancelReason()).isEqualTo(CancelReason.USER_REQUEST);
        assertThat(read.getReservedCredits()).isEqualTo(new BigDecimal("0.00"));
        assertThat(read.getReservationPrice()).isNull();
        assertThat(read.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void laOrdenInicialSeGuardaConElJugadorComoMarca() {
        MarketOrder saved = repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));

        MarketOrder read = repository.findById(saved.getId()).orElseThrow();
        Long initialPlayerId = jdbcTemplate.queryForObject(
                "SELECT initial_player_id FROM market_orders WHERE id = ?", Long.class, saved.getId());

        assertThat(read.isInitial()).isTrue();
        assertThat(read.ownerType()).isEqualTo(OwnerType.ADMIN);
        assertThat(read.pending()).isEqualTo(100);
        assertThat(initialPlayerId).isEqualTo(kane.id());
    }

    /**
     * Se consulta la columna porque desde el modelo el estado siempre se deriva de las cantidades:
     * así se ve la copia que se guarda para filtrar.
     */
    @Test
    void elEstadoDerivadoSeGuardaComoTexto() {
        MarketOrder sell = MarketOrder.sell(seller, kane.id(), 10, AT);
        sell.fill(4);

        MarketOrder saved = repository.save(sell);
        String status = jdbcTemplate.queryForObject("SELECT status FROM market_orders WHERE id = ?", String.class,
                saved.getId());

        assertThat(status).isEqualTo(OrderStatus.PARTIAL.name());
    }

    @Test
    void guardarDeNuevoUnaOrdenLaActualiza() {
        MarketOrder saved = repository.save(MarketOrder.sell(seller, kane.id(), 10, AT));
        saved.fill(10);

        repository.save(saved);

        MarketOrder read = repository.findById(saved.getId()).orElseThrow();
        assertThat(read.status()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(orderDAO.count()).isEqualTo(1);
    }

    @Test
    void unaSegundaOrdenInicialDelMismoJugadorViolaElIndiceUnico() {
        repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));

        assertThatThrownBy(() -> repository.save(MarketOrder.initialFor(superuser, kane.id(), AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(mostSpecificCauseMessage(e)).contains(INITIAL_INDEX));
    }

    @Test
    void variasOrdenesNoInicialesDelMismoJugadorConviven() {
        repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));

        List<MarketOrder> saved = repository.saveAll(List.of(
                MarketOrder.sell(seller, kane.id(), 5, AT),
                MarketOrder.sell(seller, kane.id(), 3, AT),
                MarketOrder.buy(buyer, kane.id(), 2, new BigDecimal("2.06"), RESERVATION_PRICE, AT)));

        assertThat(saved).allSatisfy(order -> assertThat(order.getId()).isNotNull());
        assertThat(orderDAO.count()).isEqualTo(4);
    }

    @Test
    void lasAbiertasDeUnJugadorSonLasNoInicialesPendientesOParcialesEnOrdenDeLlegadaYDeId() {
        repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));
        MarketOrder newest = repository.save(MarketOrder.sell(seller, kane.id(), 5, AT.plusSeconds(120)));
        MarketOrder partial = MarketOrder.sell(seller, kane.id(), 5, AT.plusSeconds(60));
        partial.fill(2);
        partial = repository.save(partial);
        MarketOrder sameInstant = repository.save(MarketOrder.sell(seller, kane.id(), 5, AT.plusSeconds(60)));
        MarketOrder executed = MarketOrder.sell(seller, kane.id(), 5, AT);
        executed.fill(5);
        repository.save(executed);
        MarketOrder cancelled = MarketOrder.sell(seller, kane.id(), 5, AT);
        cancelled.cancelRemainder(CancelReason.USER_REQUEST);
        repository.save(cancelled);
        repository.save(MarketOrder.sell(seller, salah.id(), 5, AT));

        List<MarketOrder> open = repository.findOpenOfPlayer(kane.id());

        assertThat(open).extracting(MarketOrder::getId)
                .containsExactly(partial.getId(), sameInstant.getId(), newest.getId());
    }

    @Test
    void losJugadoresSinOrdenInicialSonLosQueNoLaTienen() {
        repository.save(MarketOrder.initialFor(superuser, salah.id(), AT));

        List<Long> withoutInitial = repository.playerIdsWithoutInitialOrder();

        assertThat(withoutInitial).containsExactly(kane.id(), son.id());
    }

    @Test
    void entreLosJugadoresPedidosSoloFiguranLosQueNoTienenOrdenInicial() {
        repository.save(MarketOrder.initialFor(superuser, salah.id(), AT));

        List<Long> withoutInitial = repository.playerIdsWithoutInitialOrderIn(List.of(salah.id(), son.id()));

        assertThat(withoutInitial).containsExactly(son.id());
    }

    @Test
    void sinJugadoresPedidosNoHayNingunoSinOrdenInicial() {
        List<Long> withoutInitial = repository.playerIdsWithoutInitialOrderIn(List.of());

        assertThat(withoutInitial).isEmpty();
    }

    @Test
    void elLibroDeUnJugadorEsSuOrdenInicial() {
        MarketOrder initial = repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));

        Optional<MarketOrder> book = transactionTemplate.execute(status -> repository.lockBook(kane.id()));

        assertThat(book).isPresent();
        assertThat(book.get().getId()).isEqualTo(initial.getId());
        assertThat(book.get().pending()).isEqualTo(100);
    }

    @Test
    void unJugadorSinOrdenInicialNoTieneLibro() {
        Optional<MarketOrder> book = transactionTemplate.execute(status -> repository.lockBook(kane.id()));

        assertThat(book).isEmpty();
    }

    @Test
    void lasInicialesDelSuperusuarioSonLasQueTienenTokensConElJugadorYSuEquipo() {
        MarketOrder kaneInitial = MarketOrder.initialFor(superuser, kane.id(), AT);
        kaneInitial.fill(10);
        repository.save(kaneInitial);
        MarketOrder salahInitial = MarketOrder.initialFor(superuser, salah.id(), AT);
        salahInitial.fill(100);
        repository.save(salahInitial);
        repository.save(MarketOrder.initialFor(superuser, son.id(), AT));
        repository.save(MarketOrder.sell(seller, kane.id(), 5, AT));

        List<OrderView> initialOrders = repository.findInitialOrdersOf(superuser);

        assertThat(initialOrders).extracting(view -> view.player().name())
                .containsExactlyInAnyOrder("Harry Kane", "Son Heung-min");
        assertThat(initialOrders).allSatisfy(view -> {
            assertThat(view.order().isInitial()).isTrue();
            assertThat(view.order().getPlayerId()).isEqualTo(view.player().id());
            assertThat(view.player().team().name()).isEqualTo("Arsenal FC");
        });
        assertThat(initialOrders).filteredOn(view -> view.player().id().equals(kane.id())).singleElement()
                .satisfies(view -> assertThat(view.order().pending()).isEqualTo(90));
    }

    @Test
    void unUsuarioNoTieneOrdenesIniciales() {
        repository.save(MarketOrder.initialFor(superuser, kane.id(), AT));
        repository.save(MarketOrder.sell(seller, kane.id(), 5, AT));

        List<OrderView> initialOrders = repository.findInitialOrdersOf(seller);

        assertThat(initialOrders).isEmpty();
    }

    @Test
    void elListadoTraeSoloLasAbiertasDeLaMasViejaALaMasNuevaConSuJugadorYSuEquipo() {
        Market market = seedMarket();

        QuotePage<OrderView> listed = repository.findMarket(MarketOrderFilter.none(), 0, 10);

        assertThat(idsOf(listed)).containsExactly(market.kaneInitial(), market.musialaInitial(), market.kaneSale(),
                market.musialaPurchase());
        assertThat(listed.content()).extracting(view -> view.player().team().name())
                .containsExactly("Arsenal FC", "FC Bayern München", "Arsenal FC", "FC Bayern München");
        assertThat(listed.content()).allSatisfy(view ->
                assertThat(view.order().getPlayerId()).isEqualTo(view.player().id()));
    }

    @Test
    void filtraPorTipoDeOrden() {
        Market market = seedMarket();

        QuotePage<OrderView> purchases = repository.findMarket(filter(OrderSide.BUY, null, null, null, null), 0, 10);
        QuotePage<OrderView> sales = repository.findMarket(filter(OrderSide.SELL, null, null, null, null), 0, 10);

        assertThat(idsOf(purchases)).containsExactly(market.musialaPurchase());
        assertThat(idsOf(sales)).containsExactly(market.kaneInitial(), market.musialaInitial(), market.kaneSale());
    }

    @Test
    void filtraPorLiga() {
        Market market = seedMarket();

        QuotePage<OrderView> listed = repository.findMarket(filter(null, League.BUNDESLIGA, null, null, null), 0, 10);

        assertThat(idsOf(listed)).containsExactly(market.musialaInitial(), market.musialaPurchase());
    }

    @Test
    void filtraPorEquipoConElNombreNormalizado() {
        Market market = seedMarket();

        QuotePage<OrderView> listed = repository.findMarket(filter(null, null, "  Arsenal   FC ", null, null), 0, 10);

        assertThat(idsOf(listed)).containsExactly(market.kaneInitial(), market.kaneSale());
    }

    @Test
    void conElTipoDeUsuarioDuenoAdministradorTraeLasInicialesYConUsuarioLasDemas() {
        Market market = seedMarket();

        QuotePage<OrderView> initials = repository.findMarket(filter(null, null, null, OwnerType.ADMIN, null), 0, 10);
        QuotePage<OrderView> users = repository.findMarket(filter(null, null, null, OwnerType.USER, null), 0, 10);

        assertThat(idsOf(initials)).containsExactly(market.kaneInitial(), market.musialaInitial());
        assertThat(initials.content()).extracting(view -> view.order().pending()).containsExactly(87, 100);
        assertThat(idsOf(users)).containsExactly(market.kaneSale(), market.musialaPurchase());
    }

    @Test
    void filtraPorJugador() {
        Market market = seedMarket();

        QuotePage<OrderView> listed = repository.findMarket(filter(null, null, null, null, kane.id()), 0, 10);

        assertThat(idsOf(listed)).containsExactly(market.kaneInitial(), market.kaneSale());
    }

    @Test
    void losFiltrosSeCombinan() {
        Market market = seedMarket();

        QuotePage<OrderView> premierUserSales =
                repository.findMarket(filter(OrderSide.SELL, League.PREMIER, null, OwnerType.USER, null), 0, 10);
        QuotePage<OrderView> premierPurchases =
                repository.findMarket(filter(OrderSide.BUY, League.PREMIER, null, null, null), 0, 10);

        assertThat(idsOf(premierUserSales)).containsExactly(market.kaneSale());
        assertThat(premierPurchases.content()).isEmpty();
        assertThat(premierPurchases.totalElements()).isZero();
    }

    @Test
    void elListadoSeRecorrePorHojasConLaPaginacionDelCatalogo() {
        Market market = seedMarket();

        QuotePage<OrderView> second = repository.findMarket(MarketOrderFilter.none(), 1, 3);

        assertThat(idsOf(second)).containsExactly(market.musialaPurchase());
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.totalElements()).isEqualTo(4);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.hasPrevious()).isTrue();
        assertThat(second.hasNext()).isFalse();
    }

    /**
     * Órdenes en los cuatro estados, de dos ligas y de los dos tipos de usuario dueño. Las iniciales
     * de Kane y Musiala tienen el mismo instante, así que las desempata el id.
     */
    private Market seedMarket() {
        Team bayern = teamRepository.save(new Team(null, "5", "FC Bayern München", null, League.BUNDESLIGA, "37"));
        Player musiala = database.player("8310", "Jamal Musiala", Position.MIDFIELDER, bayern);
        MarketOrder kaneInitial = MarketOrder.initialFor(superuser, kane.id(), AT);
        kaneInitial.fill(13);
        long kaneInitialId = repository.save(kaneInitial).getId();
        MarketOrder salahInitial = MarketOrder.initialFor(superuser, salah.id(), AT);
        salahInitial.fill(100);
        repository.save(salahInitial);
        long musialaInitialId = repository.save(MarketOrder.initialFor(superuser, musiala.id(), AT)).getId();
        long kaneSaleId = repository.save(MarketOrder.sell(seller, kane.id(), 5, AT.plusSeconds(60))).getId();
        MarketOrder musialaPurchase = MarketOrder.buy(buyer, musiala.id(), 3, new BigDecimal("3.09"),
                RESERVATION_PRICE, AT.plusSeconds(120));
        musialaPurchase.fill(1);
        long musialaPurchaseId = repository.save(musialaPurchase).getId();
        MarketOrder executed = MarketOrder.sell(seller, kane.id(), 5, AT.plusSeconds(30));
        executed.fill(5);
        repository.save(executed);
        MarketOrder cancelled = MarketOrder.sell(seller, son.id(), 5, AT.plusSeconds(30));
        cancelled.cancelRemainder(CancelReason.USER_REQUEST);
        repository.save(cancelled);
        return new Market(kaneInitialId, musialaInitialId, kaneSaleId, musialaPurchaseId);
    }

    private static MarketOrderFilter filter(OrderSide side, League league, String team, OwnerType ownerType,
                                            Long playerId) {
        return new MarketOrderFilter(side, league, team, ownerType, playerId);
    }

    private static List<Long> idsOf(QuotePage<OrderView> page) {
        return page.content().stream().map(view -> view.order().getId()).toList();
    }

    /** Las órdenes abiertas del listado de prueba. */
    private record Market(long kaneInitial, long musialaInitial, long kaneSale, long musialaPurchase) {
    }

    private long user(String username, Role role, String balance) {
        AppUser user = AppUser.reconstitute(null, username, username + "@correo.com", PASSWORD_HASH, null, role,
                new BigDecimal(balance));
        return appUserRepository.save(user).getId();
    }

    private static String mostSpecificCauseMessage(Throwable e) {
        return NestedExceptionUtils.getMostSpecificCause(e).getMessage().toLowerCase(Locale.ROOT);
    }

    private void cleanDatabase() {
        orderDAO.deleteAll();
        appUserDAO.deleteAll();
        database.clean();
    }
}
