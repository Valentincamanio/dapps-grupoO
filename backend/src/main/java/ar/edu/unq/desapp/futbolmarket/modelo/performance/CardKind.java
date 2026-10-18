package ar.edu.unq.desapp.futbolmarket.modelo.performance;

/**
 * Las tarjetas que cuenta WhoScored. La segunda amarilla suma una roja: así la doble amarilla es
 * una amarilla y una roja (FR-007; research D10).
 */
public enum CardKind {
    YELLOW,
    SECOND_YELLOW,
    RED;

    /**
     * La segunda amarilla y la roja directa expulsan al jugador.
     */
    public boolean isRed() {
        return this != YELLOW;
    }
}
