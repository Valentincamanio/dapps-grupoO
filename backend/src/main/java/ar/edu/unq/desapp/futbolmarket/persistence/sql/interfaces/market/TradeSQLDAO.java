package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;

/**
 * Acceso a la tabla {@code market_trades}. Las consultas del historial y del portfolio las suman
 * sus historias; ninguna es SQL nativo (principio VI).
 */
public interface TradeSQLDAO extends JpaRepository<TradeSQL, Long> {

    /**
     * La ganancia realizada acumulada en las ventas del usuario. Nula si nunca vendió.
     */
    @Query("select sum(t.realizedProfit) from TradeSQL t where t.seller.id = :userId")
    BigDecimal sumRealizedProfitOfSeller(@Param("userId") Long userId);

    /**
     * El historial del usuario (research D21): las operaciones en las que compró o vendió, de la más
     * nueva a la más vieja, con el jugador, su equipo, la cotización usada y su estrategia en la
     * misma consulta. {@code side} es el lado del usuario: {@code BUY} las que compró y {@code SELL}
     * las que vendió. El rango es {@code [from, to)}. Un filtro nulo no filtra; el conteo va aparte
     * porque con {@code join fetch} no se puede derivar.
     */
    @Query(value = """
            select t from TradeSQL t
            join fetch t.player p
            join fetch p.team
            join fetch t.quote q
            join fetch q.strategy
            where ((:side is null and (t.buyer.id = :userId or t.seller.id = :userId))
                   or (:side = 'BUY' and t.buyer.id = :userId)
                   or (:side = 'SELL' and t.seller.id = :userId))
              and (:playerId is null or p.id = :playerId)
              and (:from is null or t.executedAt >= :from)
              and (:to is null or t.executedAt < :to)
            order by t.executedAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from TradeSQL t
            where ((:side is null and (t.buyer.id = :userId or t.seller.id = :userId))
                   or (:side = 'BUY' and t.buyer.id = :userId)
                   or (:side = 'SELL' and t.seller.id = :userId))
              and (:playerId is null or t.player.id = :playerId)
              and (:from is null or t.executedAt >= :from)
              and (:to is null or t.executedAt < :to)
            """)
    Page<TradeSQL> findHistory(@Param("userId") Long userId, @Param("side") String side,
                               @Param("playerId") Long playerId, @Param("from") Instant from,
                               @Param("to") Instant to, Pageable pageable);
}
