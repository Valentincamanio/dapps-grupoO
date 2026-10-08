package ar.edu.unq.desapp.futbolmarket.modelo.player;

import java.time.LocalDate;
import java.util.Objects;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

public record Player(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth,
                     String nationality, boolean active) {
    public Player {
        externalId = requireExternalId(externalId);
        if (name == null || name.isBlank()) {
            throw new CatalogInvariantException("El nombre del jugador es obligatorio.");
        }
        if (position == null) {
            throw new CatalogInvariantException("La posición del jugador es obligatoria.");
        }
        if (team == null) {
            throw new CatalogInvariantException("El equipo del jugador es obligatorio.");
        }
        name = name.trim();
        nationality = trimToNull(nationality);
    }

    public Player(String externalId, String name, Position position, Team team) {
        this(null, externalId, name, position, team);
    }

    public Player(Long id, String externalId, String name, Position position, Team team) {
        this(id, externalId, name, position, team, null, null, true);
    }

    public League league() {
        return team.league();
    }

    /**
     * Aplica lo que informa la fuente y conserva la identidad del jugador. Un nombre o una
     * posición que no llegan no borran los guardados (FR-013); la fecha de nacimiento y la
     * nacionalidad, en cambio, se toman aunque vengan vacías (FR-020). Volver a llegar lo
     * reactiva (FR-019).
     */
    public Player updateFrom(PlayerSnapshot snapshot, Team newTeam) {
        return new Player(
                id,
                externalId,
                Objects.requireNonNullElse(snapshot.name(), name),
                Objects.requireNonNullElse(snapshot.position(), position),
                newTeam,
                snapshot.dateOfBirth(),
                snapshot.nationality(),
                true
        );
    }

    /**
     * Un jugador que dejó las cinco ligas no se borra: queda inactivo con su último equipo (FR-017).
     */
    public Player deactivate() {
        return new Player(id, externalId, name, position, team, dateOfBirth, nationality, false);
    }

    /**
     * Compartido con {@link PlayerSnapshot}, para que un jugador informado por la fuente y uno
     * del catálogo tengan el mismo invariante y el mismo mensaje.
     */
    static String requireExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo del jugador es obligatorio.");
        }
        return externalId.trim();
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
