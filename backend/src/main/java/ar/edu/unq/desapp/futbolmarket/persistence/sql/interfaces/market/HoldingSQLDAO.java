package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.HoldingSQL;

/**
 * Acceso a la tabla {@code holdings}. Derived queries y JPQL: no hay SQL nativo (principio VI).
 */
public interface HoldingSQLDAO extends JpaRepository<HoldingSQL, Long> {

    List<HoldingSQL> findByPlayerIdAndOwnerIdIn(Long playerId, Collection<Long> ownerIds);

    /**
     * Las tenencias con tokens del titular, con el jugador y su equipo en la misma consulta: el
     * portfolio actual sin N+1 (research D19).
     */
    @Query("""
            select h from HoldingSQL h
            join fetch h.player p
            join fetch p.team
            where h.owner.id = :ownerId
              and h.quantity > 0
            """)
    List<HoldingSQL> findPositions(@Param("ownerId") Long ownerId);
}
