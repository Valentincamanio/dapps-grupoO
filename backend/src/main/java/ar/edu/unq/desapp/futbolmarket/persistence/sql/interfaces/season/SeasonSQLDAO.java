package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.season.SeasonSQL;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonSQLDAO extends JpaRepository<SeasonSQL, Long> {
    Optional<SeasonSQL> findByExternalId(String externalId);
}
