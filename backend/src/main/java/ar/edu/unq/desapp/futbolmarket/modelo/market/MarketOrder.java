package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotCancellableException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotOwnedException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Una orden de compra o de venta de tokens de un jugador (research D9).
 *
 * <p>Guarda tres cantidades (pedida, ejecutada y cancelada) y deriva de ellas el pendiente y el
 * estado, así el estado nunca contradice los números. No tiene setters: cambia solo por sus
 * métodos. Una compra lleva la reserva de su remanente y el precio por token con que se calculó;
 * una venta no reserva créditos.</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketOrder {

    private final Long id;
    private final OrderSide side;
    private final long ownerId;
    private final long playerId;
    private final int requested;
    private int executed;
    private int cancelled;
    private BigDecimal reservedCredits;
    private final BigDecimal reservationPrice;
    private final Instant createdAt;
    private final boolean initial;
    private CancelReason cancelReason;

    /**
     * La venta del superusuario por los 100 tokens del jugador (FR-007). El precio de 1,0000 no se
     * guarda: lo aplica el cruce por ser inicial.
     */
    public static MarketOrder initialFor(long superuserId, long playerId, Instant at) {
        return new MarketOrder(null, OrderSide.SELL, superuserId, playerId, MarketMath.TOKENS_PER_PLAYER, 0, 0,
                MarketMath.ZERO_CREDITS, null, at, true, null);
    }

    /**
     * Una compra que reserva de una vez todo lo que necesita: lo que se ejecuta en el acto más el
     * remanente al precio vigente (research D10).
     */
    public static MarketOrder buy(long ownerId, long playerId, int quantity, BigDecimal reservation,
                                  BigDecimal reservationPrice, Instant at) {
        requireValidQuantity(quantity);
        if (reservation == null || reservation.signum() < 0) {
            throw new MarketInvariantException("La reserva de una compra no puede ser negativa: " + reservation + ".");
        }
        if (reservationPrice == null || reservationPrice.signum() <= 0) {
            throw new MarketInvariantException(
                    "El precio de reserva de una compra tiene que ser mayor que 0: " + reservationPrice + ".");
        }
        return new MarketOrder(null, OrderSide.BUY, ownerId, playerId, quantity, 0, 0, reservation, reservationPrice,
                at, false, null);
    }

    public static MarketOrder sell(long ownerId, long playerId, int quantity, Instant at) {
        requireValidQuantity(quantity);
        return new MarketOrder(null, OrderSide.SELL, ownerId, playerId, quantity, 0, 0, MarketMath.ZERO_CREDITS,
                null, at, false, null);
    }

    /**
     * Reconstruye el estado leído de la base. No valida porque ese estado ya se validó al crearse.
     * La usa únicamente {@code MarketOrderMapper}.
     */
    public static MarketOrder reconstitute(State state) {
        return new MarketOrder(state.id(), state.side(), state.ownerId(), state.playerId(), state.requested(),
                state.executed(), state.cancelled(), state.reservedCredits(), state.reservationPrice(),
                state.createdAt(), state.initial(), state.cancelReason());
    }

    public int pending() {
        return requested - executed - cancelled;
    }

    /**
     * El estado según la tabla de research D9: con pendiente, pendiente o parcial según si ya se
     * ejecutó algo; sin pendiente, ejecutada o cancelada según si se canceló algo.
     */
    public OrderStatus status() {
        if (pending() > 0) {
            return executed == 0 ? OrderStatus.PENDING : OrderStatus.PARTIAL;
        }
        return cancelled == 0 ? OrderStatus.EXECUTED : OrderStatus.CANCELLED;
    }

    /**
     * Solo el superusuario tiene órdenes iniciales, y no tiene otras.
     */
    public OwnerType ownerType() {
        return initial ? OwnerType.ADMIN : OwnerType.USER;
    }

    public boolean isOpen() {
        return status().isOpen();
    }

    public void fill(int quantity) {
        if (quantity <= 0 || quantity > pending()) {
            throw new MarketInvariantException("No se pueden ejecutar " + quantity + " tokens de una orden con "
                    + pending() + " pendientes.");
        }
        executed += quantity;
    }

    /**
     * Paga una operación de esta compra con su reserva.
     */
    public void consumeReservation(BigDecimal amount) {
        requireBuy();
        if (amount.signum() < 0 || amount.compareTo(reservedCredits) > 0) {
            throw new MarketInvariantException("No se pueden pagar " + amount + " con una reserva de "
                    + reservedCredits + ".");
        }
        reservedCredits = reservedCredits.subtract(amount);
    }

    /**
     * Suma a la reserva de esta compra lo que su dueño reservó desde su libre, cuando una operación
     * a un precio mayor que el de reserva cuesta más de lo reservado (research D11).
     */
    public void addToReservation(BigDecimal amount) {
        requireBuy();
        if (amount.signum() < 0) {
            throw new MarketInvariantException("No se puede sumar un monto negativo a una reserva: " + amount + ".");
        }
        reservedCredits = reservedCredits.add(amount);
    }

    /**
     * Vuelve a calcular la reserva del remanente después de un cruce (research D11): la del
     * pendiente al precio de reserva, salvo que no alcance lo que la orden puede tener reservado.
     * Así el libre del dueño nunca queda negativo y no se cancela nada que todavía se pueda pagar.
     *
     * @param available la reserva actual de la orden más el libre de su dueño
     * @return cuánto cambió la reserva, para mover lo mismo en la cuenta: positivo se reserva y
     *         negativo se libera
     */
    public BigDecimal adjustReservation(BigDecimal available) {
        requireBuy();
        if (available.signum() < 0) {
            throw new MarketInvariantException("Lo disponible para una reserva no puede ser negativo: " + available + ".");
        }
        BigDecimal adjusted = MarketMath.total(pending(), reservationPrice).min(available);
        BigDecimal change = adjusted.subtract(reservedCredits);
        reservedCredits = adjusted;
        return change;
    }

    /**
     * Cancela todo el pendiente; lo ejecutado se conserva (FR-034).
     *
     * @return la reserva que se libera, para devolverla al libre del dueño (0 en una venta)
     */
    public BigDecimal cancelRemainder(CancelReason reason) {
        if (initial) {
            throw new MarketInvariantException("Una orden inicial no se cancela.");
        }
        if (!isOpen()) {
            throw new OrderNotCancellableException(status());
        }
        BigDecimal released = reservedCredits;
        cancelled += pending();
        cancelReason = reason;
        reservedCredits = MarketMath.ZERO_CREDITS;
        return released;
    }

    /**
     * Cancela por saldo insuficiente lo que una compra pendiente no puede pagar, y deja pendientes
     * solo los {@code keep} tokens que sí (FR-027). La reserva no cambia acá: la ajusta
     * {@link #adjustReservation} después de pagar lo que se ejecuta. Si no alcanza para ningún
     * token, se cancela todo con {@link #cancelRemainder}.
     */
    public void cancelBeyond(int keep) {
        requireBuy();
        if (keep <= 0 || keep >= pending()) {
            throw new MarketInvariantException("Para cancelar el resto hay que conservar entre 1 y "
                    + (pending() - 1) + " tokens: " + keep + ".");
        }
        cancelled += pending() - keep;
        cancelReason = CancelReason.INSUFFICIENT_CREDITS;
    }

    /**
     * Solo el dueño opera sobre su orden, y nadie sobre una inicial (FR-010 y research D16).
     */
    public void requireOwnedBy(long requesterId) {
        if (initial || ownerId != requesterId) {
            throw new OrderNotOwnedException();
        }
    }

    private void requireBuy() {
        if (side != OrderSide.BUY) {
            throw new MarketInvariantException("Solo una compra reserva créditos.");
        }
    }

    private static void requireValidQuantity(int quantity) {
        if (quantity < 1 || quantity > MarketMath.MAX_ORDER_QUANTITY) {
            throw new MarketInvariantException("La cantidad de una orden tiene que estar entre 1 y "
                    + MarketMath.MAX_ORDER_QUANTITY + ": " + quantity + ".");
        }
    }

    /**
     * El estado de una orden tal como se guarda, para {@link #reconstitute}. Es un record para no
     * tener una fábrica de doce parámetros.
     */
    public record State(Long id, OrderSide side, long ownerId, long playerId, int requested, int executed,
                        int cancelled, BigDecimal reservedCredits, BigDecimal reservationPrice, Instant createdAt,
                        boolean initial, CancelReason cancelReason) {
    }
}
