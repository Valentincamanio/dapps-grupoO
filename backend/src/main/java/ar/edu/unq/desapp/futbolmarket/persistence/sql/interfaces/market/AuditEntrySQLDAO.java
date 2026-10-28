package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditEntrySQL;

/**
 * Acceso a la tabla {@code audit_entries}. La consulta con filtros la suma la historia de la
 * auditoría; ninguna es SQL nativo (principio VI).
 */
public interface AuditEntrySQLDAO extends JpaRepository<AuditEntrySQL, Long> {
}
