package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

/**
 * Invariante interna de una cotización o de una semana (escalas, vigencias, el lunes de la
 * semana). Es un error de programación o de datos: no se traduce a un status propio ni se muestra.
 */
public class QuoteInvariantException extends RuntimeException {
    public QuoteInvariantException(String message) {
        super(message);
    }
}
