package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamSQLDAO extends JpaRepository<TeamSQL, Long> {
    Optional<TeamSQL> findByNameAndLeague(String name, League league);
}
