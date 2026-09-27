package ar.edu.unq.desapp.futbolmarket.shared;

/**
 * Base de las excepciones de dominio que el advice traduce a 400. No usa tipos de Spring, así
 * que el modelo de cualquier feature puede extenderla sin violar el principio I.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
