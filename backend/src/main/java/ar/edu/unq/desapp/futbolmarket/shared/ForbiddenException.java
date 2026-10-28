package ar.edu.unq.desapp.futbolmarket.shared;

/**
 * Base de las excepciones de dominio que el advice traduce a 403: el pedido es válido y el
 * usuario está identificado, pero el modelo no le permite esa acción. No usa tipos de Spring, así
 * que el modelo de cualquier feature puede extenderla sin violar el principio I.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
