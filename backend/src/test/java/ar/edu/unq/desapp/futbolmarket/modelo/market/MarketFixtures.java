package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;

/**
 * Datos de prueba del mercado, sin base de datos. No es un test.
 *
 * <p>El equipo y la estrategia son los de {@link QuoteFixtures}. Las órdenes, las cuentas y las
 * tenencias llevan id, porque el cruce desempata por id y la auditoría los registra. Los instantes
 * se escriben en hora de Argentina, como los ejemplos del spec.</p>
 */
public final class MarketFixtures {
    public static final long SUPERUSER_ID = 1L;
    public static final String INITIAL_BALANCE = "1000.00";

    private static final String NOTHING = "0.00";
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";

    private MarketFixtures() {
    }

    /**
     * Un instante escrito en hora de Argentina, como {@code "2026-08-24T12:00"}.
     */
    public static Instant at(String argentineDateTime) {
        return LocalDateTime.parse(argentineDateTime).atZone(QuoteWeek.ZONE).toInstant();
    }

    public static Player activePlayer(long id, String name) {
        return QuoteFixtures.player(id, name, Position.FORWARD, QuoteFixtures.ARSENAL);
    }

    public static Player inactivePlayer(long id, String name) {
        return QuoteFixtures.inactive(activePlayer(id, name));
    }

    /**
     * Una cotización que rige desde cualquier instante: recotizada, sobre el valor inicial.
     *
     * @param value el valor del 100 %, como {@code "103.00"}: el precio por token es la centésima parte
     */
    public static Quote quote(long id, long playerId, String value, String score, Instant validFrom) {
        return new Quote(id, playerId, QuoteWeek.containing(validFrom), validFrom, new BigDecimal(value),
                Quote.INITIAL_VALUE, new BigDecimal(score), QuoteType.REQUOTED, QuoteFixtures.STRATEGY_A.ref());
    }

    public static AppUser user(long id, String balance) {
        return user(id, balance, NOTHING);
    }

    public static AppUser user(long id, String balance, String reserved) {
        return account(id, Role.USER, balance, reserved);
    }

    public static AppUser superuser(String balance) {
        return account(SUPERUSER_ID, Role.ADMIN, balance, NOTHING);
    }

    /**
     * La orden inicial del jugador con {@code pending} tokens todavía sin vender.
     */
    public static MarketOrder initialOrder(long id, long playerId, int pending, Instant createdAt) {
        return MarketOrder.reconstitute(new MarketOrder.State(id, OrderSide.SELL, SUPERUSER_ID, playerId,
                MarketMath.TOKENS_PER_PLAYER, MarketMath.TOKENS_PER_PLAYER - pending, 0, MarketMath.ZERO_CREDITS, null,
                createdAt, true, null));
    }

    /**
     * Una compra pendiente con la reserva completa de su cantidad al precio de reserva.
     */
    public static MarketOrder buy(long id, long ownerId, long playerId, int quantity, String reservationPrice,
                                  Instant createdAt) {
        BigDecimal price = new BigDecimal(reservationPrice);
        return MarketOrder.reconstitute(new MarketOrder.State(id, OrderSide.BUY, ownerId, playerId, quantity, 0, 0,
                MarketMath.total(quantity, price), price, createdAt, false, null));
    }

    public static MarketOrder sell(long id, long ownerId, long playerId, int quantity, Instant createdAt) {
        return MarketOrder.reconstitute(new MarketOrder.State(id, OrderSide.SELL, ownerId, playerId, quantity, 0, 0,
                MarketMath.ZERO_CREDITS, null, createdAt, false, null));
    }

    /**
     * Una compra a la orden inicial del superusuario, a ese precio por token. El comprador tenía
     * {@link #INITIAL_BALANCE} antes; las órdenes no importan y van sin id.
     */
    public static Trade purchase(long id, long buyerId, long playerId, int quantity, String tokenPrice,
                                 Instant executedAt) {
        BigDecimal price = new BigDecimal(tokenPrice);
        BigDecimal total = MarketMath.total(quantity, price);
        BigDecimal before = new BigDecimal(INITIAL_BALANCE);
        return new Trade(id, executedAt, playerId, null, null, buyerId, SUPERUSER_ID, OwnerType.ADMIN, quantity,
                price, total, 1L, before, before.subtract(total), MarketMath.ZERO_CREDITS, total, 0, quantity, 0, 0,
                null);
    }

    /**
     * Una venta entre dos usuarios, a ese precio por token. Los dos tenían {@link #INITIAL_BALANCE}
     * antes; la ganancia realizada va en 0, porque quien la necesita la calcula.
     */
    public static Trade sale(long id, long sellerId, long buyerId, long playerId, int quantity, String tokenPrice,
                             Instant executedAt) {
        BigDecimal price = new BigDecimal(tokenPrice);
        BigDecimal total = MarketMath.total(quantity, price);
        BigDecimal before = new BigDecimal(INITIAL_BALANCE);
        return new Trade(id, executedAt, playerId, null, null, buyerId, sellerId, OwnerType.USER, quantity, price,
                total, 1L, before, before.subtract(total), before, before.add(total), 0, quantity, quantity, 0,
                MarketMath.ZERO_CREDITS);
    }

    /**
     * Tokens comprados de una vez por {@code cost}, sin ventas.
     */
    public static CostBasis costBasis(int quantity, String cost) {
        return new CostBasis(quantity, quantity, new BigDecimal(cost));
    }

    public static Holding holding(long id, long ownerId, long playerId, CostBasis basis, int committed) {
        return Holding.reconstitute(id, ownerId, playerId, basis, committed);
    }

    private static AppUser account(long id, Role role, String balance, String reserved) {
        return AppUser.reconstitute(id, "usuario" + id, "usuario" + id + "@correo.com", PASSWORD_HASH, null, role,
                new BigDecimal(balance), new BigDecimal(reserved), null);
    }
}
