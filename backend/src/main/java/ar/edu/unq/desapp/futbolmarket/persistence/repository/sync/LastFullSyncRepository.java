package ar.edu.unq.desapp.futbolmarket.persistence.repository.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync.LastFullSyncMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.sync.LastFullSyncSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * La última sincronización completa exitosa de cada fuente. Es lo único que se guarda de las
 * corridas (FR-054). Una base sin filas es una en la que nunca hubo una (HU3, escenario 9).
 */
@Repository
public class LastFullSyncRepository {
    private final LastFullSyncSQLDAO lastFullSyncDAO;
    private final LastFullSyncMapper lastFullSyncMapper;

    public LastFullSyncRepository(LastFullSyncSQLDAO lastFullSyncDAO, LastFullSyncMapper lastFullSyncMapper) {
        this.lastFullSyncDAO = lastFullSyncDAO;
        this.lastFullSyncMapper = lastFullSyncMapper;
    }

    @Transactional(readOnly = true)
    public Optional<LastFullSync> find(SyncSource source) {
        return lastFullSyncDAO.findById(source.name()).map(lastFullSyncMapper::toDomain);
    }

    /**
     * Guarda el momento de la fuente y reemplaza el anterior: hay una sola fila por fuente.
     */
    @Transactional
    public LastFullSync save(LastFullSync lastFullSync) {
        LastFullSyncSQL saved = lastFullSyncDAO.save(lastFullSyncMapper.toSQL(lastFullSync));
        return lastFullSyncMapper.toDomain(saved);
    }
}
