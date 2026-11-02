package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationContext;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Holding;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketMath;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.AuditEntryMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.HoldingMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.MarketOrderMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.TradeMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.HoldingSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.MarketOrderSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.AuditEntrySQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.HoldingSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.TradeSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * La base de un test de integración del mercado: borra sus cuatro tablas, que los repositories no
 * dejan borrar a propósito, y verifica las invariantes del mercado después de cada escenario. Lo
 * demás (equipos, jugadores y cotizaciones) lo carga {@link QuoteDatabase}.
 *
 * <p>No es un bean: cada test lo arma con su contexto. Configuración estándar de los IT del
 * mercado (data-model 12.3):</p>
 * <ul>
 *   <li>Una base propia por clase, con {@code ;LOCK_TIMEOUT=10000} al final de la URL. Sin
 *   administrador, con {@code @TestPropertySource} y la URL
 *   {@code jdbc:h2:mem:<nombre>;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}. Con administrador, que es el
 *   superusuario, con {@code @DynamicPropertySource}, sus credenciales y la base con un sufijo al
 *   azar, como los IT que ya lo configuran.</li>
 *   <li>El reloj es un {@code MutableClock}, publicado con una {@code @TestConfiguration} anidada y
 *   {@code @Bean @Primary}.</li>
 *   <li>Las órdenes iniciales se crean llamando directo a {@code InitialOrderService.openMissing()}.
 *   La generación automática queda apagada en el perfil test, salvo en {@code InitialOrderIT}, para
 *   no competir con el {@code @Async} del arranque.</li>
 *   <li>Después de cada escenario, {@link #assertInvariants()} y {@link #assertCreditsConserved}.</li>
 * </ul>
 *
 * <p>Borra las cuentas de usuario, que guardan su reserva, pero no la del administrador: el
 * superusuario lo crea el alta de arranque y tiene que seguir existiendo entre un test y otro. Su
 * saldo crece de un escenario a otro, así que la conservación de los créditos se verifica sobre el
 * total de las cuentas de cada escenario al empezar.</p>
 *
 * <p>También arma los datos de un escenario (cuentas con saldo y cotizaciones con la estrategia
 * activa de la base) y lee como modelo lo que quedó en las tablas del mercado.</p>
 */
public final class MarketDatabase {
    private static final List<String> OPEN_STATUSES = List.of(OrderStatus.PENDING.name(), OrderStatus.PARTIAL.name());
    private static final String PASSWORD = "campeon2022";
    private static final int SUFFIX_LENGTH = 8;

    private final AuditEntrySQLDAO auditDAO;
    private final TradeSQLDAO tradeDAO;
    private final HoldingSQLDAO holdingDAO;
    private final MarketOrderSQLDAO orderDAO;
    private final AppUserSQLDAO appUserDAO;
    private final QuoteDatabase quoteDatabase;
    private final AppUserRepository userRepository;
    private final QuoteRepository quoteRepository;
    private final StrategyRepository strategyRepository;
    private final PasswordHasher hasher;
    private final MarketOrderMapper orderMapper;
    private final HoldingMapper holdingMapper;
    private final TradeMapper tradeMapper;
    private final AuditEntryMapper auditMapper;

    public MarketDatabase(ApplicationContext context) {
        auditDAO = context.getBean(AuditEntrySQLDAO.class);
        tradeDAO = context.getBean(TradeSQLDAO.class);
        holdingDAO = context.getBean(HoldingSQLDAO.class);
        orderDAO = context.getBean(MarketOrderSQLDAO.class);
        appUserDAO = context.getBean(AppUserSQLDAO.class);
        quoteDatabase = new QuoteDatabase(context);
        userRepository = context.getBean(AppUserRepository.class);
        quoteRepository = context.getBean(QuoteRepository.class);
        strategyRepository = context.getBean(StrategyRepository.class);
        hasher = context.getBean(PasswordHasher.class);
        orderMapper = context.getBean(MarketOrderMapper.class);
        holdingMapper = context.getBean(HoldingMapper.class);
        tradeMapper = context.getBean(TradeMapper.class);
        auditMapper = context.getBean(AuditEntryMapper.class);
    }

    /**
     * Para cargar equipos, jugadores y cotizaciones.
     */
    public QuoteDatabase quoteDatabase() {
        return quoteDatabase;
    }

    /**
     * Borra la auditoría, las operaciones, las tenencias y las órdenes, en el orden de las claves
     * foráneas, después las cuentas de usuario y por último lo que carga {@link QuoteDatabase}.
     */
    public void clean() {
        auditDAO.deleteAll();
        tradeDAO.deleteAll();
        holdingDAO.deleteAll();
        orderDAO.deleteAll();
        appUserDAO.deleteAll(appUserDAO.findAll().stream()
                .filter(account -> Role.USER.name().equals(account.getRole()))
                .toList());
        quoteDatabase.clean();
    }

    /**
     * Una cuenta de usuario nueva con ese saldo y datos únicos.
     */
    public AppUser user(String balance) {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);
        return userRepository.save(AppUser.register(username, username + "@correo.com", PASSWORD,
                new BigDecimal(balance), hasher).user());
    }

    /**
     * Como {@link #user(String)}, registrada en ese instante: el portfolio a una fecha no admite
     * días anteriores (research D6).
     */
    public AppUser user(String balance, Instant registeredAt) {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);
        return userRepository.save(AppUser.register(username, username + "@correo.com", PASSWORD,
                new BigDecimal(balance), hasher, registeredAt).user());
    }

    public AppUser account(long userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    public AppUser superuser() {
        return account(userRepository.findSuperuserId().orElseThrow());
    }

    /**
     * Una cotización recotizada del jugador que rige desde {@code validFrom}, con la estrategia
     * activa de la base, como guardan las suyas los IT de la 007.
     *
     * @param value el valor del 100 %, como {@code "103.00"}
     */
    public Quote quote(Player player, String value, String score, Instant validFrom) {
        Quote quote = new Quote(null, player.id(), QuoteWeek.containing(validFrom), validFrom, new BigDecimal(value),
                Quote.INITIAL_VALUE, new BigDecimal(score), QuoteType.REQUOTED,
                strategyRepository.findCatalog().activeVersion().ref());
        return quoteRepository.saveAll(List.of(quote)).getFirst();
    }

    /**
     * Las órdenes del mercado, por id.
     */
    public List<MarketOrder> orders() {
        return orderDAO.findAll().stream().map(orderMapper::toDomain)
                .sorted(Comparator.comparing(MarketOrder::getId))
                .toList();
    }

    public List<MarketOrder> initialOrdersOf(Player player) {
        return orders().stream()
                .filter(order -> order.isInitial() && order.getPlayerId() == player.id())
                .toList();
    }

    public MarketOrder initialOrderOf(Player player) {
        List<MarketOrder> initialOrders = initialOrdersOf(player);
        assertThat(initialOrders).as("órdenes iniciales del jugador %d", player.id()).hasSize(1);
        return initialOrders.getFirst();
    }

    public List<MarketOrder> ordersOf(AppUser owner) {
        return orders().stream().filter(order -> order.getOwnerId() == owner.getId()).toList();
    }

    public Optional<Holding> holdingOf(AppUser owner, Player player) {
        return holdingDAO.findAll().stream().map(holdingMapper::toDomain)
                .filter(holding -> holding.getOwnerId() == owner.getId() && holding.getPlayerId() == player.id())
                .findFirst();
    }

    public List<Trade> trades() {
        return tradeDAO.findAll().stream().map(tradeMapper::toDomain)
                .sorted(Comparator.comparing(Trade::id))
                .toList();
    }

    /**
     * Los registros de auditoría en el orden en que se guardaron.
     */
    public List<AuditEntry> auditEntries() {
        return auditDAO.findAll().stream().map(auditMapper::toDomain)
                .sorted(Comparator.comparing(AuditEntry::id))
                .toList();
    }

    /**
     * El saldo total de esas cuentas, para {@link #assertCreditsConserved}.
     */
    public BigDecimal totalBalance(Collection<Long> accountIds) {
        return appUserDAO.findAllById(accountIds).stream()
                .map(AppUserSQL::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Las invariantes del mercado sobre lo que hay en la base: los 100 tokens de cada jugador, las
     * reservas y los comprometidos coherentes con las órdenes abiertas, y nada negativo.
     */
    public void assertInvariants() {
        List<MarketOrderSQL> orders = orderDAO.findAll();
        List<HoldingSQL> holdings = holdingDAO.findAll();
        List<AppUserSQL> accounts = appUserDAO.findAll();
        assertTokensOfEachPlayer(orders, holdings);
        assertReservations(orders, accounts);
        assertCommitments(orders, holdings);
        assertNothingNegative(accounts, holdings);
    }

    /**
     * Los créditos no se crean ni se pierden (SC-003): el saldo total de esas cuentas suma lo mismo
     * que al empezar. Reservar y liberar no mueven el total; pagar y cobrar mueven lo mismo.
     */
    public void assertCreditsConserved(Collection<Long> accountIds, BigDecimal initialTotal) {
        BigDecimal total = totalBalance(accountIds);
        assertThat(total).as("saldo total de las cuentas %s", accountIds).isEqualByComparingTo(initialTotal);
    }

    /** El pendiente de la orden inicial más las tenencias de los usuarios suman 100 (FR-026). */
    private static void assertTokensOfEachPlayer(List<MarketOrderSQL> orders, List<HoldingSQL> holdings) {
        orders.stream().filter(order -> order.getInitialPlayerId() != null).forEach(initial -> {
            long playerId = initial.getInitialPlayerId();
            int held = holdings.stream()
                    .filter(holding -> holding.getPlayer().getId() == playerId)
                    .mapToInt(HoldingSQL::getQuantity)
                    .sum();
            assertThat(pending(initial) + held).as("tokens del jugador %d", playerId)
                    .isEqualTo(MarketMath.TOKENS_PER_PLAYER);
        });
    }

    /** Lo reservado de cada cuenta es la suma de las reservas de sus compras abiertas (research D5). */
    private static void assertReservations(List<MarketOrderSQL> orders, List<AppUserSQL> accounts) {
        accounts.forEach(account -> {
            BigDecimal reserved = orders.stream()
                    .filter(order -> isOpen(order) && OrderSide.BUY.name().equals(order.getSide()))
                    .filter(order -> order.getOwner().getId().equals(account.getId()))
                    .map(MarketOrderSQL::getReservedCredits)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(account.getReservedBalance()).as("reservado de la cuenta %d", account.getId())
                    .isEqualByComparingTo(reserved);
        });
    }

    /** Los comprometidos de cada tenencia son el pendiente de las ventas abiertas de su dueño. */
    private static void assertCommitments(List<MarketOrderSQL> orders, List<HoldingSQL> holdings) {
        holdings.forEach(holding -> {
            int pendingSales = orders.stream()
                    .filter(order -> isOpen(order) && OrderSide.SELL.name().equals(order.getSide()))
                    .filter(order -> order.getInitialPlayerId() == null)
                    .filter(order -> order.getOwner().getId().equals(holding.getOwner().getId())
                            && order.getPlayer().getId().equals(holding.getPlayer().getId()))
                    .mapToInt(MarketDatabase::pending)
                    .sum();
            assertThat(holding.getCommittedQuantity()).as("comprometidos de la tenencia %d", holding.getId())
                    .isEqualTo(pendingSales);
        });
    }

    private static void assertNothingNegative(List<AppUserSQL> accounts, List<HoldingSQL> holdings) {
        accounts.forEach(account -> {
            assertThat(account.getReservedBalance()).as("reservado de la cuenta %d", account.getId())
                    .isNotNegative();
            assertThat(account.getBalance().subtract(account.getReservedBalance()))
                    .as("libre de la cuenta %d", account.getId()).isNotNegative();
        });
        holdings.forEach(holding -> {
            assertThat(holding.getCommittedQuantity()).as("comprometidos de la tenencia %d", holding.getId())
                    .isNotNegative();
            assertThat(holding.getQuantity()).as("tokens de la tenencia %d", holding.getId())
                    .isGreaterThanOrEqualTo(holding.getCommittedQuantity());
        });
    }

    private static boolean isOpen(MarketOrderSQL order) {
        return OPEN_STATUSES.contains(order.getStatus());
    }

    private static int pending(MarketOrderSQL order) {
        return order.getRequestedQuantity() - order.getExecutedQuantity() - order.getCancelledQuantity();
    }
}
