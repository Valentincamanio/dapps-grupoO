package ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces;

import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.TeamSQL;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSQLDAO extends JpaRepository<TeamSQL, Long> {
}
