package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.MarketOrderMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
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
    private final PlayerMapper playerMapper;

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

    /**
     * Las órdenes iniciales del dueño que todavía tienen tokens, cada una con su jugador: las
     * posiciones del portfolio del superusuario, en una sola consulta.
     */
    @Transactional(readOnly = true)
    public List<OrderView> findInitialOrdersOf(long ownerId) {
        return orderDAO.findInitialOrdersOf(ownerId, OPEN_STATUSES).stream().map(this::toView).toList();
    }

    /**
     * Una hoja del listado del mercado (research D22): las órdenes con tokens pendientes que cumplen
     * el filtro, de la más vieja a la más nueva, cada una con su jugador, en una sola consulta.
     */
    @Transactional(readOnly = true)
    public QuotePage<OrderView> findMarket(MarketOrderFilter filter, int page, int size) {
        String side = filter.side() == null ? null : filter.side().name();
        Boolean initial = filter.ownerType() == null ? null : filter.ownerType() == OwnerType.ADMIN;
        Page<MarketOrderSQL> rows = orderDAO.findMarket(OPEN_STATUSES, side, filter.league(), filter.team(),
                filter.playerId(), initial, PageRequest.of(page, size));
        return new QuotePage<>(rows.getContent().stream().map(this::toView).toList(), rows.getNumber(),
                rows.getSize(), rows.getTotalElements());
    }

    /**
     * El jugador de una orden, sin cargarla; vacío si la orden no existe. Corre en la transacción de
     * quien la pide, antes de bloquear el libro de ese jugador.
     */
    public Optional<Long> findPlayerIdOf(long orderId) {
        return orderDAO.findPlayerIdById(orderId);
    }

    /**
     * Una hoja de las órdenes del dueño, de la más nueva a la más vieja, cada una con su jugador.
     */
    @Transactional(readOnly = true)
    public QuotePage<OrderView> findOfOwner(long ownerId, int page, int size) {
        Page<MarketOrderSQL> rows = orderDAO.findByOwnerId(ownerId, PageRequest.of(page, size));
        return new QuotePage<>(rows.getContent().stream().map(this::toView).toList(), rows.getNumber(),
                rows.getSize(), rows.getTotalElements());
    }

    private OrderView toView(MarketOrderSQL row) {
        return new OrderView(mapper.toDomain(row), playerMapper.toDomain(row.getPlayer()));
    }

    private MarketOrderSQL toSQL(MarketOrder order) {
        return mapper.toSQL(order, appUserDAO.getReferenceById(order.getOwnerId()),
                playerDAO.getReferenceById(order.getPlayerId()));
    }
}
