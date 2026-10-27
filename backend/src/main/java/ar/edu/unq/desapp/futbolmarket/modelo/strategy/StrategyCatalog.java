package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.DuplicateStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * El agregado de todas las estrategias (D13). No tiene setters: cada operación devuelve un
 * {@link StrategyChange} con el catálogo nuevo.
 *
 * <p>La versión activa es siempre la última de la estrategia activa (no se activan versiones
 * viejas). Un catálogo con estrategias tiene exactamente una activa (FR-030); el único catálogo
 * sin activa es el vacío, y por eso la primera estrategia que se crea en él queda activa.
 */
public final class StrategyCatalog {
    public static final int MAX_NAME_LENGTH = 100;

    private static final int NO_ACTIVE = -1;

    private final List<Strategy> strategies;
    private final int activeIndex;

    /**
     * Arma el catálogo con lo guardado. {@code activeVersionId} es la versión marcada como activa,
     * que tiene que ser la última de su estrategia; es nulo solo si no hay estrategias.
     */
    public StrategyCatalog(List<Strategy> strategies, Long activeVersionId) {
        this(List.copyOf(strategies), indexOfActive(strategies, activeVersionId));
    }

    private StrategyCatalog(List<Strategy> strategies, int activeIndex) {
        requireUniqueNames(strategies);
        this.strategies = strategies;
        this.activeIndex = activeIndex;
    }

    public static StrategyCatalog empty() {
        return new StrategyCatalog(List.of(), NO_ACTIVE);
    }

    public List<Strategy> strategies() {
        return strategies;
    }

    /**
     * Registra una estrategia nueva: versión 1, inactiva (salvo en un catálogo vacío, donde es la
     * única). Factor y mínimo omitidos toman los valores por defecto.
     */
    public StrategyChange create(String name, BigDecimal scaleFactor, BigDecimal minimumValue,
                                 WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        var cleanName = requireValidName(name);
        if (findByName(cleanName) != null) {
            throw new DuplicateStrategyNameException(cleanName);
        }
        var first = new StrategyVersion(null, null, cleanName, 1,
                Objects.requireNonNullElse(scaleFactor, StrategyVersion.DEFAULT_SCALE_FACTOR),
                Objects.requireNonNullElse(minimumValue, StrategyVersion.DEFAULT_MINIMUM_VALUE),
                defaultWeights, positionWeights, null);
        var all = new ArrayList<>(strategies);
        all.add(new Strategy(null, cleanName, List.of(first)));
        if (activeIndex == NO_ACTIVE) {
            return new StrategyChange(new StrategyCatalog(List.copyOf(all), 0), List.of(first), first, null);
        }
        return new StrategyChange(new StrategyCatalog(List.copyOf(all), activeIndex), List.of(first), null, null);
    }

    /**
     * Crea la versión siguiente de una estrategia. Factor y mínimo omitidos toman los de la
     * versión anterior. Si la estrategia es la activa, la versión nueva pasa a ser la activa.
     */
    public StrategyChange newVersion(Long strategyId, BigDecimal scaleFactor, BigDecimal minimumValue,
                                     WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        var index = indexOf(strategyId);
        var strategy = strategies.get(index);
        var previous = strategy.latest();
        var next = new StrategyVersion(null, strategy.id(), strategy.name(), previous.version() + 1,
                Objects.requireNonNullElse(scaleFactor, previous.scaleFactor()),
                Objects.requireNonNullElse(minimumValue, previous.minimumValue()),
                defaultWeights, positionWeights, null);
        var all = new ArrayList<>(strategies);
        all.set(index, strategy.withVersion(next));
        var catalog = new StrategyCatalog(List.copyOf(all), activeIndex);
        if (index == activeIndex) {
            return new StrategyChange(catalog, List.of(next), next, previous.id());
        }
        return new StrategyChange(catalog, List.of(next), null, null);
    }

    /**
     * Deja activa la versión más reciente de la estrategia y desactiva la anterior. Activar la que
     * ya es la activa no cambia nada.
     */
    public StrategyChange activate(Long strategyId) {
        var index = indexOf(strategyId);
        if (index == activeIndex) {
            return StrategyChange.none(this);
        }
        var deactivated = activeIndex == NO_ACTIVE ? null : strategies.get(activeIndex).latest().id();
        return new StrategyChange(new StrategyCatalog(strategies, index), List.of(),
                strategies.get(index).latest(), deactivated);
    }

    public StrategyVersion activeVersion() {
        return activeStrategy().latest();
    }

    public Strategy activeStrategy() {
        if (activeIndex == NO_ACTIVE) {
            throw new StrategyInvariantException("No hay una estrategia activa.");
        }
        return strategies.get(activeIndex);
    }

    public boolean isActive(Long strategyId) {
        return activeIndex != NO_ACTIVE && strategyId != null
                && strategyId.equals(strategies.get(activeIndex).id());
    }

    public Strategy find(Long strategyId) {
        return strategies.get(indexOf(strategyId));
    }

    /**
     * La estrategia con ese nombre, sin distinguir mayúsculas ni espacios de los extremos, o
     * {@code null} si no hay ninguna.
     */
    public Strategy findByName(String name) {
        var key = Strategy.keyOf(name);
        return strategies.stream().filter(strategy -> strategy.nameKey().equals(key)).findFirst().orElse(null);
    }

    private int indexOf(Long strategyId) {
        return IntStream.range(0, strategies.size())
                .filter(i -> strategyId != null && strategyId.equals(strategies.get(i).id()))
                .findFirst()
                .orElseThrow(() -> new StrategyNotFoundException(strategyId));
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidStrategyNameException("El nombre de la estrategia es obligatorio.");
        }
        var clean = name.strip();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new InvalidStrategyNameException("El nombre de la estrategia admite a lo sumo "
                    + MAX_NAME_LENGTH + " caracteres.");
        }
        return clean;
    }

    private static int indexOfActive(List<Strategy> strategies, Long activeVersionId) {
        if (strategies.isEmpty()) {
            if (activeVersionId != null) {
                throw new StrategyInvariantException("Un catálogo vacío no puede tener una versión activa.");
            }
            return NO_ACTIVE;
        }
        if (activeVersionId == null) {
            throw new StrategyInvariantException("El catálogo de estrategias debe tener exactamente una versión activa.");
        }
        for (int i = 0; i < strategies.size(); i++) {
            var strategy = strategies.get(i);
            if (activeVersionId.equals(strategy.latest().id())) {
                return i;
            }
            if (strategy.versions().stream().anyMatch(version -> activeVersionId.equals(version.id()))) {
                throw new StrategyInvariantException("La versión activa " + activeVersionId
                        + " no es la última de la estrategia '" + strategy.name() + "'.");
            }
        }
        throw new StrategyInvariantException("La versión activa " + activeVersionId + " no existe en el catálogo.");
    }

    private static void requireUniqueNames(List<Strategy> strategies) {
        var keys = new HashSet<String>();
        for (var strategy : strategies) {
            if (!keys.add(strategy.nameKey())) {
                throw new StrategyInvariantException("Hay dos estrategias con el nombre '" + strategy.name() + "'.");
            }
        }
    }
}
