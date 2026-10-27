package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance.PlayerMatchStatsSQL;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PlayerMatchStatsSQLDAO extends JpaRepository<PlayerMatchStatsSQL, Long> {
    /**
     * Todas las filas con el jugador y su equipo, y el partido con su temporada y sus dos equipos,
     * en una sola consulta.
     */
    @Override
    @EntityGraph(attributePaths = {"player", "player.team", "match", "match.season", "match.homeTeam", "match.awayTeam"})
    List<PlayerMatchStatsSQL> findAll();

    /**
     * Las filas de los partidos jugados en {@code [from, to)}, con las mismas asociaciones que
     * {@link #findAll()}. Es JPQL y no un {@code Between} derivado porque ese incluye los dos
     * extremos: un partido del lunes 00:00 de Argentina caería en dos semanas.
     */
    @Query("""
            SELECT stats FROM PlayerMatchStatsSQL stats
            WHERE stats.match.utcDate >= :from
              AND stats.match.utcDate < :to
            """)
    @EntityGraph(attributePaths = {"player", "player.team", "match", "match.season", "match.homeTeam", "match.awayTeam"})
    List<PlayerMatchStatsSQL> findAllByMatchUtcDateBetween(
            @Param("from") Instant fromInclusive,
            @Param("to") Instant toExclusive
    );
}
