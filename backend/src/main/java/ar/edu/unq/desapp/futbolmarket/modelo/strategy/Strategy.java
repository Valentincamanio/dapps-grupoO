package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Una estrategia con todas sus versiones, ordenadas de la 1 a la última. Su {@code id} es el de la
 * versión 1 (D13) y es nulo hasta que se guarda.
 *
 * <p>Una estrategia eliminada ({@code deleted}) se conserva porque las cotizaciones que calculó
 * apuntan a sus versiones, pero ya no se lista ni se puede usar. Al eliminarla se renombra (por
 * ejemplo, "X (eliminada el 01/11/2026 14:35)") para que su nombre quede libre y el historial la
 * distinga de una nueva con el mismo nombre; {@code originalName} guarda el que tenía, y es nulo si
 * no se eliminó.</p>
 */
public record Strategy(Long id, String name, List<StrategyVersion> versions, boolean deleted, String originalName) {
    public Strategy(Long id, String name, List<StrategyVersion> versions) {
        this(id, name, versions, false, null);
    }

    public Strategy(Long id, String name, List<StrategyVersion> versions, boolean deleted) {
        this(id, name, versions, deleted, null);
    }

    public Strategy {
        if (name == null || name.isBlank()) {
            throw new StrategyInvariantException("El nombre de la estrategia es obligatorio.");
        }
        if (versions == null || versions.isEmpty()) {
            throw new StrategyInvariantException("La estrategia '" + name + "' no tiene versiones.");
        }
        versions = versions.stream().sorted(Comparator.comparingInt(StrategyVersion::version)).toList();
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).version() != i + 1) {
                throw new StrategyInvariantException("Las versiones de la estrategia '" + name
                        + "' deben ir de la 1 a la " + versions.size() + " sin saltos.");
            }
        }
    }

    public StrategyVersion latest() {
        return versions.get(versions.size() - 1);
    }

    /**
     * El nombre sin espacios de los extremos y en minúsculas: con esta clave se decide si dos
     * nombres son el mismo (FR-028).
     */
    public String nameKey() {
        return keyOf(name);
    }

    /**
     * Si alguna vez se llamó así: por su nombre, o por el que tenía antes de eliminarla.
     */
    public boolean wasNamed(String candidate) {
        var key = keyOf(candidate);
        return nameKey().equals(key) || (originalName != null && keyOf(originalName).equals(key));
    }

    static String keyOf(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    Strategy withVersion(StrategyVersion version) {
        var all = new ArrayList<>(versions);
        all.add(version);
        return new Strategy(id, name, all, deleted, originalName);
    }

    /**
     * La misma estrategia eliminada y con el nombre nuevo, también en cada versión.
     */
    Strategy markDeleted(String newName) {
        var renamed = versions.stream().map(version -> version.renamed(newName)).toList();
        return new Strategy(id, newName, renamed, true, name);
    }
}
