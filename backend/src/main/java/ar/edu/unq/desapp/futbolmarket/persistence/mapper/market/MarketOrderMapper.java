package ar.edu.unq.desapp.futbolmarket.persistence.mapper.market;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.MarketOrderSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;

/**
 * Traduce una orden campo a campo. Los enums viajan como su nombre; la marca de inicial, como
 * {@code initial_player_id}; y el estado derivado se copia para poder filtrar por él. Del dueño y
 * del jugador solo toma el id, sin cargarlos.
 */
@Component
public class MarketOrderMapper {

    public MarketOrder toDomain(MarketOrderSQL row) {
        return MarketOrder.reconstitute(new MarketOrder.State(
                row.getId(),
                OrderSide.valueOf(row.getSide()),
                row.getOwner().getId(),
                row.getPlayer().getId(),
                row.getRequestedQuantity(),
                row.getExecutedQuantity(),
                row.getCancelledQuantity(),
                row.getReservedCredits(),
                row.getReservationPrice(),
                row.getCreatedAt(),
                row.getInitialPlayerId() != null,
                row.getCancelReason() == null ? null : CancelReason.valueOf(row.getCancelReason())));
    }

    /**
     * {@code owner} y {@code player} son las referencias de las filas a las que apunta: el
     * repository las obtiene sin cargarlas. Si el id viene en {@code null}, JPA inserta; si no,
     * actualiza.
     */
    public MarketOrderSQL toSQL(MarketOrder order, AppUserSQL owner, PlayerSQL player) {
        MarketOrderSQL row = new MarketOrderSQL();
        row.setId(order.getId());
        row.setSide(order.getSide().name());
        row.setOwner(owner);
        row.setPlayer(player);
        row.setRequestedQuantity(order.getRequested());
        row.setExecutedQuantity(order.getExecuted());
        row.setCancelledQuantity(order.getCancelled());
        row.setStatus(order.status().name());
        row.setReservedCredits(order.getReservedCredits());
        row.setReservationPrice(order.getReservationPrice());
        row.setCreatedAt(order.getCreatedAt());
        row.setInitialPlayerId(order.isInitial() ? order.getPlayerId() : null);
        row.setCancelReason(order.getCancelReason() == null ? null : order.getCancelReason().name());
        return row;
    }
}
