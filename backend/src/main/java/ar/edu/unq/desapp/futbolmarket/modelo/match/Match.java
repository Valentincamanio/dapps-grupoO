package ar.edu.unq.desapp.futbolmarket.modelo.match;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Partido de una temporada. El resultado y el ganador faltan hasta que el partido se juega
 * (FR-022).
 */
public record Match(Long id, String externalId, Season season, Instant utcDate, Integer matchday, MatchStatus status,
                    Team homeTeam, Team awayTeam, Score fullTime, Score halfTime, MatchWinner winner) {
    static final String HOME_TEAM_REQUIRED = "El equipo local del partido es obligatorio.";
    static final String AWAY_TEAM_REQUIRED = "El equipo visitante del partido es obligatorio.";

    public Match {
        externalId = requireExternalId(externalId);
        if (season == null) {
            throw new CatalogInvariantException("La temporada del partido es obligatoria.");
        }
        requireUtcDate(utcDate);
        if (status == null) {
            throw new CatalogInvariantException("El estado del partido es obligatorio.");
        }
        if (homeTeam == null) {
            throw new CatalogInvariantException(HOME_TEAM_REQUIRED);
        }
        if (awayTeam == null) {
            throw new CatalogInvariantException(AWAY_TEAM_REQUIRED);
        }
        requireDifferentTeams(homeTeam.externalId(), awayTeam.externalId());
    }

    /**
     * Conserva la identidad y la temporada, y toma lo demás de la fuente. Un partido postergado o
     * que pasó a {@code FINISHED} es el mismo partido, actualizado (HU2, escenario 4).
     */
    public Match updateFrom(MatchSnapshot snapshot, Team home, Team away) {
        return new Match(id, externalId, season, snapshot.utcDate(), snapshot.matchday(), snapshot.status(),
                home, away, snapshot.fullTime(), snapshot.halfTime(), snapshot.winner());
    }

    /**
     * Compartido con {@link MatchSnapshot}, para que un partido informado por la fuente y uno
     * guardado tengan los mismos invariantes y los mismos mensajes.
     */
    static String requireExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo del partido es obligatorio.");
        }
        return externalId.trim();
    }

    static void requireUtcDate(Instant utcDate) {
        if (utcDate == null) {
            throw new CatalogInvariantException("La fecha y la hora del partido son obligatorias.");
        }
    }

    static void requireDifferentTeams(String homeTeamExternalId, String awayTeamExternalId) {
        if (homeTeamExternalId.equals(awayTeamExternalId)) {
            throw new CatalogInvariantException("El equipo local y el visitante de un partido tienen que ser distintos.");
        }
    }
}
