package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

/**
 * Se lanza cuando no hay una estrategia con el id pedido (el id de su versión 1). Responde 404.
 */
public class StrategyNotFoundException extends NotFoundException {
    public StrategyNotFoundException(Long strategyId) {
        super("No existe una estrategia con id " + strategyId + ".");
    }
}
