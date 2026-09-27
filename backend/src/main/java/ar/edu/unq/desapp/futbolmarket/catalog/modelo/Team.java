package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

import java.util.Objects;

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
