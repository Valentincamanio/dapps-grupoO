package ar.edu.unq.desapp.futbolmarket.modelo.market.exception;

/**
 * Invariante interna del mercado: cantidades, reservas o saldos que el modelo nunca debería dejar
 * así. Como {@code QuoteInvariantException}, es un error de programación o de datos: no se traduce
 * a un status propio ni se muestra.
 */
public class MarketInvariantException extends RuntimeException {
    public MarketInvariantException(String message) {
        super(message);
    }
}
