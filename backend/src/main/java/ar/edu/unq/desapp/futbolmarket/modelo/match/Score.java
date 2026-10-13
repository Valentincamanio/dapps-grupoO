package ar.edu.unq.desapp.futbolmarket.modelo.match;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Goles de local y de visitante. Cada valor puede faltar: la fuente informa el resultado solo
 * cuando el partido se jugó (FR-022).
 */
public record Score(Integer home, Integer away) {
    public Score {
        if (isNegative(home) || isNegative(away)) {
            throw new CatalogInvariantException("Los goles de un resultado no pueden ser negativos.");
        }
    }

    private static boolean isNegative(Integer goals) {
        return goals != null && goals < 0;
    }
}
