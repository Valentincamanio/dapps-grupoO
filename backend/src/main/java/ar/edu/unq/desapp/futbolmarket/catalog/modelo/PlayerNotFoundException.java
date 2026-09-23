package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

public class PlayerNotFoundException extends RuntimeException {
    public PlayerNotFoundException(Long playerId) {
        super("No se encontró el jugador con id " + playerId + ".");
    }
}
