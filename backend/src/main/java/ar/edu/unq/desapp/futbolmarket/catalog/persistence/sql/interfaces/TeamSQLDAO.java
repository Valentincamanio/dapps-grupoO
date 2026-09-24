package ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces;

import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.TeamSQL;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamSQLDAO extends JpaRepository<TeamSQL, Long> {
    Optional<TeamSQL> findByNameAndLeague(String name, League league);
}
