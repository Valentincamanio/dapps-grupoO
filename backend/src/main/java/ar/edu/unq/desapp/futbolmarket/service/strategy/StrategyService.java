package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.DuplicateStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * Registra y consulta estrategias (US4). Las reglas (nombre único, pesos, factor y mínimo) son de
 * {@link StrategyCatalog}; este servicio lee el catálogo, aplica la operación y la guarda.
 *
 * <p>Crear una estrategia no cambia la activa ni ninguna cotización, así que no toca el caché.
 * Editar y activar van por {@link StrategyChangeService}.</p>
 */
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategyRepository;

    /**
     * Crea la versión 1, inactiva. Si dos altas con el mismo nombre se cruzan, la segunda choca con
     * el índice único {@code (name, version_number)} y se informa igual que un nombre repetido.
     */
    @Transactional
    public StrategyDetail create(String name, BigDecimal scaleFactor, BigDecimal minimumValue,
                                 WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        StrategyChange change = strategyRepository.findCatalog()
                .create(name, scaleFactor, minimumValue, defaultWeights, positionWeights);
        String createdName = change.versionsToInsert().getFirst().name();
        StrategyCatalog saved;
        try {
            saved = strategyRepository.save(change);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateStrategyNameException(createdName);
        }
        return StrategyDetail.of(saved, saved.findByName(createdName));
    }

    /**
     * Todas las estrategias, en orden de creación, con todas sus versiones.
     */
    public List<StrategyDetail> list() {
        StrategyCatalog catalog = strategyRepository.findCatalog();
        return catalog.strategies().stream().map(strategy -> StrategyDetail.of(catalog, strategy)).toList();
    }

    /**
     * La estrategia con ese id (el de su versión 1); si no existe, 404.
     */
    public StrategyDetail get(Long strategyId) {
        return StrategyDetail.of(strategyRepository.findCatalog(), strategyId);
    }
}
