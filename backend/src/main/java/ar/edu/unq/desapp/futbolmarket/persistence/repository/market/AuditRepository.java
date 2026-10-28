package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.AuditEntryMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditEntrySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.AuditEntrySQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Guarda y lee la auditoría. A propósito no tiene ningún método de actualización ni de borrado: un
 * registro no se modifica ni se borra (FR-051).
 */
@Repository
@RequiredArgsConstructor
public class AuditRepository {
    private final AuditEntrySQLDAO auditDAO;
    private final AuditEntryMapper mapper;

    /**
     * Inserta los registros en el orden recibido, que es el de los hechos, y los devuelve con su id.
     */
    @Transactional
    public List<AuditEntry> saveAll(Collection<AuditEntry> entries) {
        List<AuditEntrySQL> rows = entries.stream().map(mapper::toSQL).toList();
        return auditDAO.saveAll(rows).stream().map(mapper::toDomain).toList();
    }

    public Optional<AuditEntry> findById(long entryId) {
        return auditDAO.findById(entryId).map(mapper::toDomain);
    }
}
