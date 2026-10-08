package ar.edu.unq.desapp.futbolmarket.modelo.team;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;

/**
 * Un equipo tal como lo informa la fuente, con su plantel en el orden de la fuente. Ese orden
 * importa: decide en qué equipo queda un jugador informado en dos planteles (research D9).
 */
public record TeamSnapshot(String externalId, String name, String crest, List<PlayerSnapshot> squad) {
    public TeamSnapshot {
        externalId = Team.requireExternalId(externalId);
        name = Team.requireName(name);
        squad = squad == null ? List.of() : List.copyOf(squad);
    }

    public Team toNewTeam(League league) {
        return new Team(externalId, name, crest, league);
    }
}
