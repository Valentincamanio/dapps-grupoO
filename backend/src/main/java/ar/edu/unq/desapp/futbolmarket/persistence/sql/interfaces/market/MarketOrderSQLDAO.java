package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.MarketOrderSQL;
import jakarta.persistence.LockModeType;

/**
 * Las consultas de órdenes son derivadas o JPQL, nunca SQL nativo (principio VI). La que bloquea es
 * de una sola tabla y sin {@code join fetch} (research D17).
 */
public interface MarketOrderSQLDAO extends JpaRepository<MarketOrderSQL, Long> {

    /**
     * La orden inicial del jugador con su fila bloqueada hasta el final de la transacción: es el
     * candado del libro del jugador (research D13).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MarketOrderSQL> findByInitialPlayerId(Long initialPlayerId);

    /**
     * Las órdenes de usuarios del jugador con tokens pendientes, de la más vieja a la más nueva y
     * con el id como desempate, que es el orden del cruce (research D10).
     */
    @Query("""
            select o from MarketOrderSQL o
            where o.player.id = :playerId
              and o.initialPlayerId is null
              and o.status in :statuses
            order by o.createdAt, o.id
            """)
    List<MarketOrderSQL> findOpenOfPlayer(@Param("playerId") Long playerId,
                                          @Param("statuses") Collection<String> statuses);

    @Query("""
            select p.id from PlayerSQL p
            where not exists (select o.id from MarketOrderSQL o where o.initialPlayerId = p.id)
            order by p.id
            """)
    List<Long> findPlayerIdsWithoutInitialOrder();

    @Query("""
            select p.id from PlayerSQL p
            where p.id in :playerIds
              and not exists (select o.id from MarketOrderSQL o where o.initialPlayerId = p.id)
            order by p.id
            """)
    List<Long> findPlayerIdsWithoutInitialOrderIn(@Param("playerIds") Collection<Long> playerIds);

    /**
     * Las órdenes iniciales del dueño con tokens pendientes, con el jugador y su equipo en la misma
     * consulta: el portfolio del superusuario (research D7).
     */
    @Query("""
            select o from MarketOrderSQL o
            join fetch o.player p
            join fetch p.team
            where o.owner.id = :ownerId
              and o.initialPlayerId is not null
              and o.status in :statuses
            """)
    List<MarketOrderSQL> findInitialOrdersOf(@Param("ownerId") Long ownerId,
                                             @Param("statuses") Collection<String> statuses);
}
