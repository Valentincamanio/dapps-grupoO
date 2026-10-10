package ar.edu.unq.desapp.futbolmarket.shared;

/**
 * Base de las excepciones de dominio que el advice traduce a 503: la operación existe, pero la
 * aplicación no la puede prestar con la configuración actual. No usa tipos de Spring, así que el
 * modelo de cualquier feature puede extenderla sin violar el principio I.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
