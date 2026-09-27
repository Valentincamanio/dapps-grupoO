package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

public record Player(Long id, String externalId, String name, Position position, Team team) {
    public Player {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo del jugador es obligatorio.");
        }
        if (name == null || name.isBlank()) {
            throw new CatalogInvariantException("El nombre del jugador es obligatorio.");
        }
        if (position == null) {
            throw new CatalogInvariantException("La posición del jugador es obligatoria.");
        }
        if (team == null) {
            throw new CatalogInvariantException("El equipo del jugador es obligatorio.");
        }
        externalId = externalId.trim();
        name = name.trim();
    }

    public Player(String externalId, String name, Position position, Team team) {
        this(null, externalId, name, position, team);
    }

    public League league() {
        return team.league();
    }
}
