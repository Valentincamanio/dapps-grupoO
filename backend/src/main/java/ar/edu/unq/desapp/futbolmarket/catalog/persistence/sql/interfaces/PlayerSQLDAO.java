package ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces;

import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.PlayerSQL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerSQLDAO extends JpaRepository<PlayerSQL, Long> {
    Page<PlayerSQL> findAllByOrderByIdAsc(Pageable pageable);

    Optional<PlayerSQL> findByExternalId(String externalId);
}
