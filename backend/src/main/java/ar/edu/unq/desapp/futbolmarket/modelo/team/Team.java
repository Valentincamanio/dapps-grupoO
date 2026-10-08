package ar.edu.unq.desapp.futbolmarket.modelo.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

public record Team(Long id, String externalId, String name, String crest, League league) {
    public Team {
        externalId = requireExternalId(externalId);
        name = requireName(name);
        if (league == null) {
            throw new CatalogInvariantException("La liga del equipo es obligatoria.");
        }
        crest = trimToNull(crest);
    }

    public Team(String externalId, String name, String crest, League league) {
        this(null, externalId, name, crest, league);
    }

    /**
     * Conserva la identidad del equipo y toma lo que informa la fuente: el nombre oficial, el
     * escudo y la liga en la que juega ahora (FR-008, FR-009 y FR-020).
     */
    public Team updateFrom(TeamSnapshot snapshot, League newLeague) {
        return new Team(id, externalId, snapshot.name(), snapshot.crest(), newLeague);
    }

    /**
     * Compartido con {@link TeamSnapshot}, para que un equipo informado por la fuente y uno del
     * catálogo tengan el mismo invariante y el mismo mensaje.
     */
    static String requireExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo del equipo es obligatorio.");
        }
        return externalId.trim();
    }

    static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new CatalogInvariantException("El nombre del equipo es obligatorio.");
        }
        return name.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
