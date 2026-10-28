package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.MarketOrderMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.MarketOrderSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Guarda y lee órdenes del mercado: recibe y devuelve {@link MarketOrder}, y delega en
 * {@link MarketOrderMapper} la conversión.
 *
 * <p>Las lecturas del camino de un pedido no abren una transacción propia: corren en la del
 * servicio, que es la que mantiene los bloqueos (research D13).</p>
 */
@Repository
@RequiredArgsConstructor
public class MarketOrderRepository {
    private static final List<String> OPEN_STATUSES = List.of(OrderStatus.PENDING.name(), OrderStatus.PARTIAL.name());

    private final MarketOrderSQLDAO orderDAO;
    private final AppUserSQLDAO appUserDAO;
    private final PlayerSQLDAO playerDAO;
    private final MarketOrderMapper mapper;

    /**
     * Inserta o actualiza la orden. El dueño y el jugador se referencian sin cargarlos.
     */
    @Transactional
    public MarketOrder save(MarketOrder order) {
        return mapper.toDomain(orderDAO.save(toSQL(order)));
    }

    @Transactional
    public List<MarketOrder> saveAll(Collection<MarketOrder> orders) {
        List<MarketOrderSQL> rows = orders.stream().map(this::toSQL).toList();
        return orderDAO.saveAll(rows).stream().map(mapper::toDomain).toList();
    }

    /**
     * La orden inicial del jugador, con su fila bloqueada hasta que termine la transacción de quien
     * la pide: el candado del libro (research D13). Vacía si el jugador no tiene orden inicial.
     */
    public Optional<MarketOrder> lockBook(long playerId) {
        return MarketLocks.orBusy(() -> orderDAO.findByInitialPlayerId(playerId)).map(mapper::toDomain);
    }

    /**
     * Las órdenes de usuarios del jugador con tokens pendientes, en el orden del cruce.
     */
    public List<MarketOrder> findOpenOfPlayer(long playerId) {
        return orderDAO.findOpenOfPlayer(playerId, OPEN_STATUSES).stream().map(mapper::toDomain).toList();
    }

    /**
     * Los jugadores del catálogo que todavía no tienen su orden inicial, de menor a mayor id.
     */
    public List<Long> playerIdsWithoutInitialOrder() {
        return orderDAO.findPlayerIdsWithoutInitialOrder();
    }

    /**
     * Lo mismo, entre esos jugadores. Sin ids no se consulta la base.
     */
    public List<Long> playerIdsWithoutInitialOrderIn(Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return orderDAO.findPlayerIdsWithoutInitialOrderIn(playerIds);
    }

    public Optional<MarketOrder> findById(long orderId) {
        return orderDAO.findById(orderId).map(mapper::toDomain);
    }

    private MarketOrderSQL toSQL(MarketOrder order) {
        return mapper.toSQL(order, appUserDAO.getReferenceById(order.getOwnerId()),
                playerDAO.getReferenceById(order.getPlayerId()));
    }
}
