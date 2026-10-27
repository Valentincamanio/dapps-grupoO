package ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando un juego de pesos no cumple sus reglas (negativos, más de dos decimales, suma
 * distinta de 1,00 o juego faltante). Responde 400.
 */
public class InvalidWeightsException extends BadRequestException {
    public InvalidWeightsException(String message) {
        super(message);
    }
}
