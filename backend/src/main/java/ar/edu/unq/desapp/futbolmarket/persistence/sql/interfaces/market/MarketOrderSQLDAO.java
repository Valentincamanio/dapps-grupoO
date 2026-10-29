package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
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

    /**
     * El listado del mercado (research D22): las órdenes con tokens pendientes, con el jugador y su
     * equipo en la misma consulta, de la más vieja a la más nueva y con el id como desempate. Un
     * filtro nulo no filtra. El tipo de usuario dueño sale de la marca de inicial ({@code initial}
     * verdadero: las del superusuario; falso: las de los usuarios): solo el superusuario tiene órdenes
     * iniciales, y no tiene otras. El conteo va aparte porque con {@code join fetch} no se puede
     * derivar.
     */
    @Query(value = """
            select o from MarketOrderSQL o
            join fetch o.player p
            join fetch p.team t
            where o.status in :statuses
              and (:side is null or o.side = :side)
              and (:league is null or t.league = :league)
              and (:team is null or t.name = :team)
              and (:playerId is null or p.id = :playerId)
              and (:initial is null
                   or (:initial = true and o.initialPlayerId is not null)
                   or (:initial = false and o.initialPlayerId is null))
            order by o.createdAt, o.id
            """,
            countQuery = """
            select count(o) from MarketOrderSQL o
            join o.player p
            join p.team t
            where o.status in :statuses
              and (:side is null or o.side = :side)
              and (:league is null or t.league = :league)
              and (:team is null or t.name = :team)
              and (:playerId is null or p.id = :playerId)
              and (:initial is null
                   or (:initial = true and o.initialPlayerId is not null)
                   or (:initial = false and o.initialPlayerId is null))
            """)
    Page<MarketOrderSQL> findMarket(@Param("statuses") Collection<String> statuses, @Param("side") String side,
                                    @Param("league") League league, @Param("team") String team,
                                    @Param("playerId") Long playerId, @Param("initial") Boolean initial,
                                    Pageable pageable);
}
