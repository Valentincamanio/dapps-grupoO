package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match.MatchSQL;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchSQLDAO extends JpaRepository<MatchSQL, Long> {
    @EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})
    List<MatchSQL> findAllByExternalIdIn(Collection<String> externalIds);

    @EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})
    List<MatchSQL> findAllBySeasonExternalId(String seasonExternalId);

    @EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})
    List<MatchSQL> findAllByWhoscoredIdIsNotNull();

    @EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})
    List<MatchSQL> findAllByStatsLoadedTrue();

    /**
     * Recibe el estado como texto porque la columna {@code status} es texto: con un
     * {@code MatchStatus} Hibernate rechaza el tipo al enlazar el parámetro.
     */
    @EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})
    List<MatchSQL> findAllByStatus(String status);
}
