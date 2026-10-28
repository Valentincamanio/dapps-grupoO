package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.HoldingSQL;

/**
 * Acceso a la tabla {@code holdings}. Solo derived queries: no hay SQL nativo (principio VI).
 */
public interface HoldingSQLDAO extends JpaRepository<HoldingSQL, Long> {

    List<HoldingSQL> findByPlayerIdAndOwnerIdIn(Long playerId, Collection<Long> ownerIds);
}
