package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza al pedir eliminar la estrategia activa: siempre tiene que haber una que cotice. Responde
 * 409.
 */
public class ActiveStrategyDeletionException extends ConflictException {
    public ActiveStrategyDeletionException(String name) {
        super("No se puede eliminar '" + name + "' porque es la estrategia activa: primero hay que activar otra.");
    }
}
