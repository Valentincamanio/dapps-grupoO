package ar.edu.unq.desapp.futbolmarket.modelo.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Equipo del catálogo. {@code whoscoredId} queda en {@code null} hasta que el equipo se cruza con
 * WhoScored, y un cruce nunca cambia (FR-016).
 */
public record Team(Long id, String externalId, String name, String crest, League league, String whoscoredId) {
    public Team {
        externalId = requireExternalId(externalId);
        name = requireName(name);
        if (league == null) {
            throw new CatalogInvariantException("La liga del equipo es obligatoria.");
        }
        crest = trimToNull(crest);
        whoscoredId = trimToNull(whoscoredId);
    }

    public Team(Long id, String externalId, String name, String crest, League league) {
        this(id, externalId, name, crest, league, null);
    }

    public Team(String externalId, String name, String crest, League league) {
        this(null, externalId, name, crest, league);
    }

    /**
     * Conserva la identidad del equipo y toma lo que informa la fuente: el nombre oficial, el
     * escudo y la liga en la que juega ahora (FR-008, FR-009 y FR-020). También conserva el cruce
     * con WhoScored: si no, la sincronización con Football-Data lo borraría.
     */
    public Team updateFrom(TeamSnapshot snapshot, League newLeague) {
        return new Team(id, externalId, snapshot.name(), snapshot.crest(), newLeague, whoscoredId);
    }

    public boolean isCrossed() {
        return whoscoredId != null;
    }

    /**
     * El equipo cruzado con ese identificador de WhoScored. Volver a cruzarlo con el mismo no
     * cambia nada; con otro, rompería un cruce que tiene que ser para siempre.
     */
    public Team crossWith(String whoscoredId) {
        Team crossed = new Team(id, externalId, name, crest, league, whoscoredId);
        if (isCrossed() && !this.whoscoredId.equals(crossed.whoscoredId())) {
            throw new CatalogInvariantException("El equipo ya está cruzado con otro identificador de WhoScored.");
        }
        return crossed;
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
