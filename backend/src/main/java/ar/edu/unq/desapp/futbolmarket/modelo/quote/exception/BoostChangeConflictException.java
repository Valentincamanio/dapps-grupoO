package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;

/**
 * Se lanza cuando guardar los potenciados de una semana choca con otro cambio de la misma semana
 * que se cruzó con este. No se guarda nada. Responde 409.
 */
public class BoostChangeConflictException extends ConflictException {
    public BoostChangeConflictException() {
        super("Los potenciados de la semana cambiaron mientras se guardaban: reintente.");
    }
}
