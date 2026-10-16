package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.sync.LastFullSyncSQL;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastFullSyncSQLDAO extends JpaRepository<LastFullSyncSQL, String> {
}
