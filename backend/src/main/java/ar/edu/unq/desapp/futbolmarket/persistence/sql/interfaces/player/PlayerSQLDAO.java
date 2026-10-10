package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerSQLDAO extends JpaRepository<PlayerSQL, Long> {
    Page<PlayerSQL> findAllByActiveTrueOrderByIdAsc(Pageable pageable);

    @Query("""
            SELECT player FROM PlayerSQL player
            JOIN player.team team
            WHERE (:league IS NULL OR team.league = :league)
              AND (:team IS NULL OR team.name = :team)
              AND (:position IS NULL OR player.position = :position)
              AND player.active = true
            """)
    Page<PlayerSQL> findAllByFilters(
            @Param("league") League league,
            @Param("team") String team,
            @Param("position") Position position,
            Pageable pageable
    );

    Optional<PlayerSQL> findByExternalId(String externalId);

    @EntityGraph(attributePaths = "team")
    List<PlayerSQL> findAllByExternalIdIn(Collection<String> externalIds);

    @EntityGraph(attributePaths = "team")
    List<PlayerSQL> findAllByActiveTrue();
}
