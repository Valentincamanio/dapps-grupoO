package ar.edu.unq.desapp.futbolmarket.shared;

/**
 * Base de las excepciones de dominio que el advice traduce a 404. No usa tipos de Spring, así
 * que el modelo de cualquier feature puede extenderla sin violar el principio I.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
