package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import lombok.RequiredArgsConstructor;

/**
 * Activar una estrategia y editarla (crear su versión siguiente), research D14.
 *
 * <p>No es transaccional, como {@code SyncService}: la escritura va en
 * {@link StrategyChangeWriter} y, si la versión activa cambió, el caché se vacía recién después del
 * commit (D21), porque el ranking informa la estrategia activa. Editar una estrategia que no es la
 * activa guarda la versión nueva y no toca la activa, las cotizaciones ni el caché. Por ahora todo
 * cambio rige desde la semana siguiente: no se recotiza ni se toman candados.</p>
 */
@Service
@RequiredArgsConstructor
public class StrategyChangeService {

    private final StrategyRepository strategyRepository;
    private final StrategyChangeWriter changeWriter;
    private final QuoteCacheInvalidator cacheInvalidator;

    /**
     * Deja activa la versión más reciente de la estrategia. Activar la que ya es la activa no
     * escribe nada.
     */
    public StrategyDetail activate(Long strategyId) {
        StrategyChange change = strategyRepository.findCatalog().activate(strategyId);
        return apply(change, strategyId);
    }

    /**
     * Crea la versión siguiente con estos parámetros. Factor y mínimo omitidos conservan los de la
     * versión anterior; si la estrategia es la activa, la versión nueva pasa a ser la activa.
     */
    public StrategyDetail createVersion(Long strategyId, BigDecimal scaleFactor, BigDecimal minimumValue,
                                        WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        StrategyChange change = strategyRepository.findCatalog()
                .newVersion(strategyId, scaleFactor, minimumValue, defaultWeights, positionWeights);
        return apply(change, strategyId);
    }

    private StrategyDetail apply(StrategyChange change, Long strategyId) {
        if (change.isEmpty()) {
            return StrategyDetail.of(change.catalog(), strategyId);
        }
        StrategyCatalog saved = changeWriter.apply(change);
        if (change.changesActive()) {
            cacheInvalidator.invalidateAll();
        }
        return StrategyDetail.of(saved, strategyId);
    }
}
