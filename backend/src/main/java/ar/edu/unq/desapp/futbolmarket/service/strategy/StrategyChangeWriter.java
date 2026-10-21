package ar.edu.unq.desapp.futbolmarket.service.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * La parte transaccional de un cambio de estrategia (research D14), como {@code SyncWriteService}
 * en la 005: {@link StrategyChangeService} decide y este bean escribe.
 *
 * <p>{@link #apply(StrategyChange)} es el cambio "la semana siguiente": guarda la versión nueva y
 * mueve la marca {@code active} en una sola transacción, sin recotizar y sin tomar candados. Un
 * recálculo en curso termina con la estrategia que leyó al empezar (FR-039).</p>
 */
@Service
@RequiredArgsConstructor
public class StrategyChangeWriter {

    private final StrategyRepository strategyRepository;

    @Transactional
    public StrategyCatalog apply(StrategyChange change) {
        return strategyRepository.save(change);
    }
}
