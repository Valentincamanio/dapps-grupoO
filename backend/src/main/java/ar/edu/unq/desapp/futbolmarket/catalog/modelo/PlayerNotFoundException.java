package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

public class PlayerNotFoundException extends NotFoundException {
    public PlayerNotFoundException(Long playerId) {
        super("No se encontró el jugador con id " + playerId + ".");
    }
}
