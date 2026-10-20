package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote;

import java.time.LocalDate;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
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
     * La mayor semana con una cotización de ese tipo, o {@code null} si no hay ninguna. Usa el índice
     * {@code ix_quotes_week_start_type}.
     */
    @Query("select max(q.weekStart) from QuoteSQL q where q.type = :type")
    LocalDate findLastWeekStartOfType(@Param("type") QuoteType type);
}
