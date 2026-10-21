package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import java.util.List;
import java.util.Optional;

/**
 * El resultado de una operación de {@link StrategyCatalog}: el catálogo nuevo y lo que la
 * persistencia tiene que hacer para llegar a él.
 *
 * <p>{@code activatedVersion} es la versión que pasa a ser la activa (puede ser una de
 * {@code versionsToInsert}, todavía sin id) y {@code deactivatedVersionId} la fila que deja de
 * serlo. Los dos son nulos cuando la activa no cambia; {@code deactivatedVersionId} también lo es
 * cuando el catálogo no tenía activa (estaba vacío).
 */
public record StrategyChange(StrategyCatalog catalog, List<StrategyVersion> versionsToInsert,
                             StrategyVersion activatedVersion, Long deactivatedVersionId) {
    public StrategyChange {
        versionsToInsert = List.copyOf(versionsToInsert);
    }

    static StrategyChange none(StrategyCatalog catalog) {
        return new StrategyChange(catalog, List.of(), null, null);
    }

    public Optional<StrategyVersion> activated() {
        return Optional.ofNullable(activatedVersion);
    }

    public Optional<Long> deactivated() {
        return Optional.ofNullable(deactivatedVersionId);
    }

    public boolean changesActive() {
        return activatedVersion != null;
    }

    /**
     * Si no hay nada que guardar (por ejemplo, activar la estrategia que ya es la activa).
     */
    public boolean isEmpty() {
        return versionsToInsert.isEmpty() && !changesActive();
    }
}
