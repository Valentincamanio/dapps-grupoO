package ar.edu.unq.desapp.futbolmarket.modelo.team;

import java.util.Objects;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

public record Team(Long id, String name, League league) {
    public Team {
        if (name == null || name.isBlank()) {
            throw new CatalogInvariantException("El nombre del equipo es obligatorio.");
        }
        if (league == null) {
            throw new CatalogInvariantException("La liga del equipo es obligatoria.");
        }
        name = name.trim();
    }

    public Team(String name, League league) {
        this(null, name, league);
    }
}
