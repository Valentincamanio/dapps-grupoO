package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance.PlayerMatchStatsSQL;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerMatchStatsSQLDAO extends JpaRepository<PlayerMatchStatsSQL, Long> {
    /**
     * Todas las filas con el jugador y su equipo, y el partido con su temporada y sus dos equipos,
     * en una sola consulta.
     */
    @Override
    @EntityGraph(attributePaths = {"player", "player.team", "match", "match.season", "match.homeTeam", "match.awayTeam"})
    List<PlayerMatchStatsSQL> findAll();
}
