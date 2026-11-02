package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Un registro de la auditoría (research D18): una orden creada, una operación ejecutada o una
 * cancelación, con su autor, los datos del hecho, el antes y el después de cada parte y una frase
 * en español que lo describe.
 *
 * <p>Es inmutable: un registro guardado no se modifica ni se borra (FR-051). Se crea solo con sus
 * fábricas, que deciden qué campos lleva cada tipo de hecho y dejan nulos los demás. En una
 * operación, la parte primaria es el comprador y la secundaria el vendedor; en los otros hechos
 * hay una sola parte, el dueño de la orden.</p>
 */
public record AuditEntry(Long id, Instant occurredAt, AuditEventType eventType, AuditActorType actorType,
                         Long actorId, long playerId, Long orderId, Long tradeId, OrderSide side, int quantity,
                         BigDecimal tokenPrice, BigDecimal total, Long quoteId, CancelReason cancelReason,
                         PartyChange primary, PartyChange secondary, String detail) {

    public static final int MAX_DETAIL_LENGTH = 500;

    public AuditEntry {
        if (occurredAt == null || eventType == null || primary == null || detail == null) {
            throw new MarketInvariantException("Un registro de auditoría necesita su instante, su tipo, una parte y su detalle.");
        }
        AuditActor.requireValid(actorType, actorId);
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new MarketInvariantException("El detalle de un registro de auditoría tiene hasta " + MAX_DETAIL_LENGTH
                    + " caracteres: " + detail.length() + ".");
        }
    }

    public static AuditEntry orderCreated(MarketOrder order, Player player, AuditActor actor, PartyChange change,
                                          Instant at) {
        requireOrderOf(order, player, change);
        return new AuditEntry(null, at, AuditEventType.ORDER_CREATED, actor.type(), actor.id(), order.getPlayerId(),
                order.getId(), null, order.getSide(), order.getRequested(), null, null, null, null, change, null,
                createdDetail(order, player));
    }

    /**
     * La orden del registro es la de quien hizo el pedido, que puede ser el comprador o el vendedor.
     */
    public static AuditEntry tradeExecuted(Trade trade, Player player, AuditActor actor, PartyChange buyer,
                                           PartyChange seller) {
        if (actor.id() == null || player.id() == null || player.id() != trade.playerId()
                || buyer.userId() != trade.buyerId() || seller.userId() != trade.sellerId()) {
            throw new MarketInvariantException("El registro de una operación no coincide con su jugador o sus partes.");
        }
        OrderSide side = trade.sideFor(actor.id());
        Long orderId = side == OrderSide.BUY ? trade.buyOrderId() : trade.sellOrderId();
        return new AuditEntry(null, trade.executedAt(), AuditEventType.TRADE_EXECUTED, actor.type(), actor.id(),
                trade.playerId(), orderId, trade.id(), side, trade.quantity(), trade.tokenPrice(), trade.total(),
                trade.quoteId(), null, buyer, seller, tradeDetail(trade, player));
    }

    /**
     * Se arma con la orden ya cancelada: la cantidad y el motivo son los de su cancelación.
     */
    public static AuditEntry orderCancelled(MarketOrder order, Player player, AuditActor actor, PartyChange change,
                                            Instant at) {
        requireOrderOf(order, player, change);
        if (order.getCancelled() == 0 || order.getCancelReason() == null) {
            throw new MarketInvariantException("Una orden sin tokens cancelados no tiene registro de cancelación.");
        }
        return new AuditEntry(null, at, AuditEventType.ORDER_CANCELLED, actor.type(), actor.id(), order.getPlayerId(),
                order.getId(), null, order.getSide(), order.getCancelled(), null, null, null, order.getCancelReason(),
                change, null, cancelledDetail(order, player));
    }

    /**
     * Una copia con los ids de la orden y de la operación, que se conocen recién al guardarlas: el
     * libro arma el registro antes (data-model 2.10). El registro original no cambia.
     */
    public AuditEntry withReferences(Long order, Long trade) {
        return new AuditEntry(id, occurredAt, eventType, actorType, actorId, playerId, order, trade, side, quantity,
                tokenPrice, total, quoteId, cancelReason, primary, secondary, detail);
    }

    private static void requireOrderOf(MarketOrder order, Player player, PartyChange change) {
        if (player.id() == null || player.id() != order.getPlayerId() || change.userId() != order.getOwnerId()) {
            throw new MarketInvariantException("El registro de una orden no coincide con su jugador o su dueño.");
        }
    }

    private static String createdDetail(MarketOrder order, Player player) {
        String tokens = MarketFormat.tokens(order.getRequested()) + " de " + player.name();
        if (order.isInitial()) {
            return "Orden inicial de venta de " + tokens + " a " + MarketFormat.price(MarketMath.INITIAL_TOKEN_PRICE);
        }
        if (order.getSide() == OrderSide.BUY) {
            return "Orden de compra de " + tokens + " con " + MarketFormat.credits(order.getReservedCredits())
                    + " créditos reservados";
        }
        return "Orden de venta de " + tokens;
    }

    private static String tradeDetail(Trade trade, Player player) {
        String counterpart = trade.sellerType() == OwnerType.ADMIN ? "contra la orden inicial" : "entre usuarios";
        return "Compra de " + MarketFormat.tokens(trade.quantity()) + " de " + player.name() + " a "
                + MarketFormat.price(trade.tokenPrice()) + " por " + MarketFormat.credits(trade.total()) + " "
                + counterpart;
    }

    private static String cancelledDetail(MarketOrder order, Player player) {
        String reason = order.getCancelReason() == CancelReason.USER_REQUEST
                ? "a pedido del usuario"
                : "por saldo insuficiente";
        String side = order.getSide() == OrderSide.BUY ? "compra" : "venta";
        return "Cancelación de " + MarketFormat.tokens(order.getCancelled()) + " de la orden de " + side + " de "
                + player.name() + " " + reason;
    }
}
