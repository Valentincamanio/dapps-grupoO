package ar.edu.unq.desapp.futbolmarket.modelo.player;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PlayerSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Un jugador tal como figura en un plantel de la fuente.
 *
 * <p>A diferencia de {@link Player}, el nombre y la posición pueden faltar: un jugador nuevo
 * incompleto se saltea (FR-012) y uno guardado conserva lo que ya tenía (FR-013). Sin
 * {@code externalId}, en cambio, no hay forma de reconocerlo, así que es un error de formato.</p>
 */
public record PlayerSnapshot(String externalId, String name, Position position, LocalDate dateOfBirth,
                             String nationality) {
    public PlayerSnapshot {
        externalId = Player.requireExternalId(externalId);
        name = Player.trimToNull(name);
        nationality = Player.trimToNull(nationality);
    }

    public boolean isComplete() {
        return name != null && position != null;
    }

    /**
     * El primer dato obligatorio que falta. Solo tiene sentido con un snapshot incompleto.
     */
    public PlayerSkipReason missingDataReason() {
        return name == null ? PlayerSkipReason.MISSING_NAME : PlayerSkipReason.MISSING_POSITION;
    }

    public Player toNewPlayer(Team team) {
        return new Player(null, externalId, name, position, team, dateOfBirth, nationality, true);
    }
}
