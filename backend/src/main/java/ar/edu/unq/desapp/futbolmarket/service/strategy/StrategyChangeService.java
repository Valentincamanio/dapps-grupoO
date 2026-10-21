package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationLock;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationReportLogger;
import lombok.RequiredArgsConstructor;

/**
 * Activar una estrategia y editarla (crear su versión siguiente), research D14.
 *
 * <p>No es transaccional, como {@code SyncService}: la escritura va en
 * {@link StrategyChangeWriter} y el caché se vacía recién después del commit (D21), porque el
 * ranking informa la estrategia activa.</p>
 *
 * <p>La opción {@link RequoteTiming} aplica solo si el cambio deja activa una versión distinta de
 * la que cotizó la última semana (FR-033): activar otra, editar la activa, o activar la activa si
 * la última semana se cotizó con otra versión. Crear o editar una estrategia que no es la activa la
 * ignora. Con {@link RequoteTiming#NEXT_WEEK} el cambio se guarda sin recotizar ni tomar candados;
 * con {@link RequoteTiming#NOW} se toma el {@link RecalculationLock} afuera de la transacción, así
 * que si hay un recálculo o una sincronización en curso el 409 sale antes de escribir nada
 * (FR-038), y se suelta después del commit.</p>
 */
@Service
@RequiredArgsConstructor
public class StrategyChangeService {

    private final StrategyRepository strategyRepository;
    private final StrategyChangeWriter changeWriter;
    private final QuoteCacheInvalidator cacheInvalidator;
    private final RecalculationLock recalculationLock;
    private final QuoteRepository quoteRepository;
    private final RecalculationReportLogger reportLogger;
    private final Clock clock;

    /**
     * Deja activa la versión más reciente de la estrategia, desde la semana siguiente. Activar la
     * que ya es la activa no escribe nada.
     */
    public StrategyDetail activate(Long strategyId) {
        return activate(strategyId, RequoteTiming.NEXT_WEEK).strategy();
    }

    /**
     * Deja activa la versión más reciente de la estrategia y, con {@link RequoteTiming#NOW},
     * recotiza en el acto. Activar la que ya es la activa en la versión que cotizó la última semana
     * no hace nada.
     */
    public StrategyChangeResult activate(Long strategyId, RequoteTiming timing) {
        StrategyChange change = strategyRepository.findCatalog().activate(strategyId);
        return apply(change, strategyId, timing);
    }

    /**
     * Crea la versión siguiente con estos parámetros, desde la semana siguiente. Factor y mínimo
     * omitidos conservan los de la versión anterior; si la estrategia es la activa, la versión nueva
     * pasa a ser la activa.
     */
    public StrategyDetail createVersion(Long strategyId, BigDecimal scaleFactor, BigDecimal minimumValue,
                                        WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        return createVersion(strategyId, scaleFactor, minimumValue, defaultWeights, positionWeights,
                RequoteTiming.NEXT_WEEK).strategy();
    }

    /**
     * Crea la versión siguiente. Si la estrategia es la activa, la versión nueva pasa a ser la
     * activa y rige la opción; si no, la opción se ignora.
     */
    public StrategyChangeResult createVersion(Long strategyId, BigDecimal scaleFactor, BigDecimal minimumValue,
                                              WeightSet defaultWeights, Map<Position, WeightSet> positionWeights,
                                              RequoteTiming timing) {
        StrategyChange change = strategyRepository.findCatalog()
                .newVersion(strategyId, scaleFactor, minimumValue, defaultWeights, positionWeights);
        return apply(change, strategyId, timing);
    }

    private StrategyChangeResult apply(StrategyChange change, Long strategyId, RequoteTiming timing) {
        if (timing == RequoteTiming.NOW && requoteApplies(change, strategyId)) {
            return applyAndRequote(change, strategyId);
        }
        return StrategyChangeResult.withoutRequotation(applyNextWeek(change, strategyId));
    }

    private StrategyDetail applyNextWeek(StrategyChange change, Long strategyId) {
        if (change.isEmpty()) {
            return StrategyDetail.of(change.catalog(), strategyId);
        }
        StrategyCatalog saved = changeWriter.apply(change);
        if (change.changesActive()) {
            cacheInvalidator.invalidateAll();
        }
        return StrategyDetail.of(saved, strategyId);
    }

    private StrategyChangeResult applyAndRequote(StrategyChange change, Long strategyId) {
        RecalculationReport report = recalculationLock.runExclusively(
                () -> changeWriter.applyAndRequote(change, clock.instant()));
        cacheInvalidator.invalidateAll();
        reportLogger.log(report);
        return new StrategyChangeResult(StrategyDetail.of(strategyRepository.findCatalog(), strategyId),
                Optional.of(report));
    }

    /**
     * Si el cambio deja activa una versión distinta de la que cotizó la última semana. Activar otra
     * o editar la activa siempre la cambian; activar la activa, solo si la última semana cerrada
     * tiene alguna cotización de otra versión.
     */
    private boolean requoteApplies(StrategyChange change, Long strategyId) {
        if (change.changesActive()) {
            return true;
        }
        StrategyCatalog catalog = change.catalog();
        return change.isEmpty() && catalog.isActive(strategyId)
                && lastWeekQuotedWithAnother(catalog.activeVersion());
    }

    private boolean lastWeekQuotedWithAnother(StrategyVersion active) {
        return quoteRepository.findLatestOfWeek(QuoteWeek.lastClosedAt(clock.instant())).values().stream()
                .anyMatch(quote -> !active.id().equals(quote.strategy().versionId()));
    }
}
