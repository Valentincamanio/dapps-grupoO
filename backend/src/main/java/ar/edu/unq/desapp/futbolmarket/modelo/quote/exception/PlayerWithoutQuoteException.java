package ar.edu.unq.desapp.futbolmarket.modelo.quote.exception;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

/**
 * Se lanza cuando el jugador existe pero todavía no tiene ninguna cotización. Es un 404 con un
 * mensaje propio, distinto del de un jugador inexistente (research D20, FR-041).
 */
public class PlayerWithoutQuoteException extends NotFoundException {

    public PlayerWithoutQuoteException(long playerId) {
        super("El jugador " + playerId + " no tiene cotización.");
    }
}
