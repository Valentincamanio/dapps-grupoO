package ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces;

import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.PlayerSQL;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSQLDAO extends JpaRepository<PlayerSQL, Long> {
}
