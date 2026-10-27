package ar.edu.unq.desapp.futbolmarket.modelo.quote;

/**
 * El origen de una cotización (research D10): la inicial de 100,00 de un jugador que no tenía
 * ninguna, la calculada al cierre de una semana y la recotizada en el acto al cambiar la estrategia.
 */
public enum QuoteType {
    INITIAL,
    CALCULATED,
    REQUOTED
}
