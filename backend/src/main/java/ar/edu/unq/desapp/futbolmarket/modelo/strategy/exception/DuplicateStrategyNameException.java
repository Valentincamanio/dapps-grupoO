package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando ya hay una estrategia con ese nombre, sin distinguir mayúsculas ni espacios de
 * los extremos. Responde 409.
 */
public class DuplicateStrategyNameException extends ConflictException {
    public DuplicateStrategyNameException(String name) {
        super("Ya existe una estrategia con el nombre '" + name + "'.");
    }
}
