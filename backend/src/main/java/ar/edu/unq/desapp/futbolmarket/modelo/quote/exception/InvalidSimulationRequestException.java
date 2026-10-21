package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando una simulación trae una estrategia guardada y parámetros, o ninguna de las dos
 * cosas (FR-054). Responde 400.
 */
public class InvalidSimulationRequestException extends BadRequestException {

    public InvalidSimulationRequestException() {
        super("Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna.");
    }
}
