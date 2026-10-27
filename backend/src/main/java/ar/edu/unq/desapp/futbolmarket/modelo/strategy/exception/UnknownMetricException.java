package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando un peso nombra una métrica que no existe. Responde 400 con el nombre recibido.
 */
public class UnknownMetricException extends BadRequestException {
    public UnknownMetricException(String metricName) {
        super("La métrica '" + metricName + "' no existe.");
    }
}
