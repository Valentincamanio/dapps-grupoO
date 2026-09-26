package ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.PlayerSQL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerSQLDAO extends JpaRepository<PlayerSQL, Long> {
    Page<PlayerSQL> findAllByOrderByIdAsc(Pageable pageable);

    @Query("""
            SELECT player FROM PlayerSQL player
            JOIN player.team team
            WHERE (:league IS NULL OR team.league = :league)
              AND (:team IS NULL OR team.name = :team)
              AND (:position IS NULL OR player.position = :position)
            """)
    Page<PlayerSQL> findAllByFilters(
            @Param("league") League league,
            @Param("team") String team,
            @Param("position") Position position,
            Pageable pageable
    );

    Optional<PlayerSQL> findByExternalId(String externalId);
}
