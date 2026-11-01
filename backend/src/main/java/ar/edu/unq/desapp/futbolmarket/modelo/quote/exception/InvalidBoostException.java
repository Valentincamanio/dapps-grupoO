package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando los potenciados de una semana no cumplen las reglas: cuatro jugadores distintos (o
 * ninguno) y cada porcentaje de 200 % a 400 %, de a 25 %. Responde 400.
 */
public class InvalidBoostException extends BadRequestException {
    public InvalidBoostException(String message) {
        super(message);
    }
}
