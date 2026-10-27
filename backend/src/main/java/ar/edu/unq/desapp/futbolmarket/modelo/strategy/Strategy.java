package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Una estrategia con todas sus versiones, ordenadas de la 1 a la última. Su {@code id} es el de la
 * versión 1 (D13) y es nulo hasta que se guarda.
 */
public record Strategy(Long id, String name, List<StrategyVersion> versions) {
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

    static String keyOf(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    Strategy withVersion(StrategyVersion version) {
        var all = new ArrayList<>(versions);
        all.add(version);
        return new Strategy(id, name, all);
    }
}
