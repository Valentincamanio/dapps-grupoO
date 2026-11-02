package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditEntrySQL;

/**
 * Acceso a la tabla {@code audit_entries}. Ninguna consulta es SQL nativo (principio VI).
 */
public interface AuditEntrySQLDAO extends JpaRepository<AuditEntrySQL, Long> {

    /**
     * La auditoría filtrada (research D18), de la más nueva a la más vieja. {@code userId} filtra por
     * el autor o por cualquiera de las dos partes; el rango es {@code [from, to)}. Un filtro nulo no
     * filtra. Las partes son columnas de la misma fila, así que no hace falta ningún {@code join}.
     */
    @Query(value = """
            select a from AuditEntrySQL a
            where (:eventType is null or a.eventType = :eventType)
              and (:userId is null or a.actorId = :userId or a.primary.userId = :userId
                   or a.secondary.userId = :userId)
              and (:playerId is null or a.playerId = :playerId)
              and (:from is null or a.occurredAt >= :from)
              and (:to is null or a.occurredAt < :to)
            order by a.occurredAt desc, a.id desc
            """,
            countQuery = """
            select count(a) from AuditEntrySQL a
            where (:eventType is null or a.eventType = :eventType)
              and (:userId is null or a.actorId = :userId or a.primary.userId = :userId
                   or a.secondary.userId = :userId)
              and (:playerId is null or a.playerId = :playerId)
              and (:from is null or a.occurredAt >= :from)
              and (:to is null or a.occurredAt < :to)
            """)
    Page<AuditEntrySQL> findAudit(@Param("eventType") String eventType, @Param("userId") Long userId,
                                  @Param("playerId") Long playerId, @Param("from") Instant from,
                                  @Param("to") Instant to, Pageable pageable);
}
