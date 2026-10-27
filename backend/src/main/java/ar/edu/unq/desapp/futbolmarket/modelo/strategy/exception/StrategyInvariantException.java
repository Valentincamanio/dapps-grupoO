package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

/**
 * Invariante interna del catálogo de estrategias (por ejemplo, que haya exactamente una versión
 * activa). Es un error de programación o de datos: no se traduce a un status propio.
 */
public class StrategyInvariantException extends RuntimeException {
    public StrategyInvariantException(String message) {
        super(message);
    }
}
