package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando los datos de una cuenta no cumplen las invariantes del modelo. El mensaje es
 * el de la regla incumplida y nunca repite el valor recibido. Responde 400.
 */
public class InvalidUserDataException extends BadRequestException {

    public InvalidUserDataException(String message) {
        super(message);
    }
}
