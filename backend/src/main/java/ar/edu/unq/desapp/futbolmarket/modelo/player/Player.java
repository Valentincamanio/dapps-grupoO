package ar.edu.unq.desapp.futbolmarket.modelo.player;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.OptionalInt;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Jugador del catálogo. {@code whoscoredId} queda en {@code null} hasta que el jugador se cruza
 * con WhoScored, y un cruce nunca cambia (FR-020 y FR-022).
 */
public record Player(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth,
                     String nationality, boolean active, String whoscoredId) {
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
        whoscoredId = trimToNull(whoscoredId);
    }

    public Player(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth,
                  String nationality, boolean active) {
        this(id, externalId, name, position, team, dateOfBirth, nationality, active, null);
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
     * reactiva (FR-019). El cruce con WhoScored se conserva.
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
                true,
                whoscoredId
        );
    }

    /**
     * Un jugador que dejó las cinco ligas no se borra: queda inactivo con su último equipo
     * (FR-017) y con su cruce con WhoScored.
     */
    public Player deactivate() {
        return new Player(id, externalId, name, position, team, dateOfBirth, nationality, false, whoscoredId);
    }

    public boolean isCrossed() {
        return whoscoredId != null;
    }

    /**
     * El jugador cruzado con ese identificador de WhoScored. Volver a cruzarlo con el mismo no
     * cambia nada; con otro, rompería un cruce que tiene que ser para siempre.
     */
    public Player crossWith(String whoscoredId) {
        Player crossed = new Player(id, externalId, name, position, team, dateOfBirth, nationality, active, whoscoredId);
        if (isCrossed() && !this.whoscoredId.equals(crossed.whoscoredId())) {
            throw new CatalogInvariantException("El jugador ya está cruzado con otro identificador de WhoScored.");
        }
        return crossed;
    }

    /**
     * La edad cumplida ese día, para desempatar homónimos al cruzar (FR-019). Sin fecha de
     * nacimiento no hay edad.
     */
    public OptionalInt ageOn(LocalDate day) {
        if (dateOfBirth == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Period.between(dateOfBirth, day).getYears());
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
