package ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.strategy.StrategyMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee y guarda el catálogo de estrategias. No actualiza ni borra versiones: lo único que cambia
 * en una fila guardada es la marca {@code active} (research D13).
 */
@Repository
@RequiredArgsConstructor
public class StrategyRepository {
    private final StrategySQLDAO strategyDAO;
    private final StrategyMapper strategyMapper;

    /**
     * Todas las estrategias con sus versiones y pesos, y la versión activa.
     */
    @Transactional(readOnly = true)
    public StrategyCatalog findCatalog() {
        return strategyMapper.toCatalog(strategyDAO.findAll());
    }

    /**
     * Inserta las versiones nuevas del cambio (las siguientes a la 1 apuntan a la raíz) y mueve
     * la marca {@code active}. Devuelve el catálogo guardado, con los ids generados.
     */
    @Transactional
    public StrategyCatalog save(StrategyChange change) {
        change.deactivated().ifPresent(versionId -> strategyDAO.getReferenceById(versionId).deactivate());
        for (StrategyVersion version : change.versionsToInsert()) {
            strategyDAO.save(strategyMapper.toSQL(version, rootOf(version), version.equals(change.activatedVersion())));
        }
        change.activated()
                .filter(version -> version.id() != null)
                .ifPresent(version -> strategyDAO.getReferenceById(version.id()).activate());
        return strategyMapper.toCatalog(strategyDAO.findAll());
    }

    private StrategySQL rootOf(StrategyVersion version) {
        return version.strategyId() == null ? null : strategyDAO.getReferenceById(version.strategyId());
    }
}
