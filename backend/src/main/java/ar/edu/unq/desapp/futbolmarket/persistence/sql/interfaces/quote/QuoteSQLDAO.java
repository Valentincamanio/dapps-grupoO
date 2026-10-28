package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Las consultas de cotizaciones son JPQL o derivadas, nunca SQL nativo (Principio VI). Cada
 * historia suma las suyas.
 */
public interface QuoteSQLDAO extends JpaRepository<QuoteSQL, Long> {

    /**
     * Por jugador, la cotización de mayor vigencia de las semanas anteriores a la dada, del tipo que
     * sea (también las recotizadas, research D9). Trae la versión que la calculó, que el mapper lee.
     * El índice único {@code (player_id, valid_from)} resuelve la subconsulta.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.weekStart < :weekStart
              and q.validFrom = (select max(previous.validFrom) from QuoteSQL previous
                                 where previous.player = q.player and previous.weekStart < :weekStart)
            """)
    List<QuoteSQL> findLastOfEachPlayerBefore(@Param("weekStart") LocalDate weekStart);

    /**
     * Por jugador, la cotización de mayor vigencia de esa semana, del tipo que sea: la calculada o
     * la última recotizada (research D12). Trae la versión que la calculó, que el mapper lee.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.weekStart = :weekStart
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other
                                 where other.player = q.player and other.weekStart = :weekStart)
            """)
    List<QuoteSQL> findLatestOfEachPlayerIn(@Param("weekStart") LocalDate weekStart);

    /**
     * La mayor semana con una cotización de ese tipo, o {@code null} si no hay ninguna. Usa el índice
     * {@code ix_quotes_week_start_type}.
     */
    @Query("select max(q.weekStart) from QuoteSQL q where q.type = :type")
    LocalDate findLastWeekStartOfType(@Param("type") QuoteType type);

    /**
     * La cotización de mayor vigencia del jugador: la actual. El índice único
     * {@code (player_id, valid_from)} garantiza que es una sola.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.player.id = :playerId
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other where other.player.id = :playerId)
            """)
    Optional<QuoteSQL> findCurrent(@Param("playerId") Long playerId);

    /**
     * La cotización actual de cada uno de esos jugadores. Los que no tienen ninguna no figuran.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.player.id in :playerIds
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other where other.player = q.player)
            """)
    List<QuoteSQL> findCurrentOf(@Param("playerIds") Collection<Long> playerIds);

    /**
     * La cotización actual de cada jugador que tiene alguna, activo o no: la de mayor vigencia.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.validFrom = (select max(other.validFrom) from QuoteSQL other where other.player = q.player)
            """)
    List<QuoteSQL> findLatestOfEachPlayer();

    /**
     * La última cotización del jugador con vigencia anterior al instante, que queda excluido.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.player.id = :playerId
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other
                                 where other.player.id = :playerId and other.validFrom < :end)
            """)
    Optional<QuoteSQL> findLastBefore(@Param("playerId") Long playerId, @Param("end") Instant end);

    /**
     * La cotización del jugador que rige en el instante: la última con vigencia anterior o igual a
     * él (research D14 de la 008). A diferencia de {@link #findLastBefore}, el instante se incluye.
     */
    @Query("""
            select q from QuoteSQL q join fetch q.strategy
            where q.player.id = :playerId
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other
                                 where other.player.id = :playerId and other.validFrom <= :instant)
            """)
    Optional<QuoteSQL> findInForceAt(@Param("playerId") Long playerId, @Param("instant") Instant instant);

    /**
     * Todas las cotizaciones del jugador con vigencia en {@code [from, to)}, también las calculadas
     * que una recotización dejó de vigentes, de la más vieja a la más nueva.
     */
    @Query(value = """
            select q from QuoteSQL q join fetch q.strategy
            where q.player.id = :playerId and q.validFrom >= :from and q.validFrom < :to
            order by q.validFrom asc
            """,
            countQuery = """
            select count(q) from QuoteSQL q
            where q.player.id = :playerId and q.validFrom >= :from and q.validFrom < :to
            """)
    Page<QuoteSQL> findHistory(@Param("playerId") Long playerId, @Param("from") Instant from,
                               @Param("to") Instant to, Pageable pageable);

    /**
     * La última cotización de cada jugador activo que cumple los filtros, con el jugador, su equipo
     * y la versión en la misma consulta. Ordena por valor descendente y desempata por nombre y por
     * id, así el orden es total y la paginación estable. Los filtros nulos no filtran, como en el
     * catálogo.
     */
    @Query(value = """
            select q from QuoteSQL q
            join fetch q.player player
            join fetch player.team team
            join fetch q.strategy
            where player.active = true
              and (:league is null or team.league = :league)
              and (:team is null or team.name = :team)
              and (:position is null or player.position = :position)
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other where other.player = q.player)
            order by q.value desc, player.name asc, player.id asc
            """,
            countQuery = """
            select count(q) from QuoteSQL q
            join q.player player
            join player.team team
            where player.active = true
              and (:league is null or team.league = :league)
              and (:team is null or team.name = :team)
              and (:position is null or player.position = :position)
              and q.validFrom = (select max(other.validFrom) from QuoteSQL other where other.player = q.player)
            """)
    Page<QuoteSQL> findRanking(@Param("league") League league, @Param("team") String team,
                               @Param("position") Position position, Pageable pageable);
}
