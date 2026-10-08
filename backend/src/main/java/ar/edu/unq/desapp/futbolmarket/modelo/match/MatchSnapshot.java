package ar.edu.unq.desapp.futbolmarket.modelo.match;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Un partido tal como lo informa la fuente, con los equipos por su {@code externalId}.
 *
 * <p>Valida acá todo lo que {@link Match} exige y no depende del catálogo: así un dato imposible
 * hace fallar la liga como error de formato, y {@code Match} nunca falla al escribir, dentro de
 * la transacción de la liga (research D7). El estado y la temporada, en cambio, pueden faltar:
 * un estado desconocido omite el partido sin hacer fallar la liga.</p>
 */
public record MatchSnapshot(String externalId, String seasonExternalId, Instant utcDate, Integer matchday,
                            MatchStatus status, String homeTeamExternalId, String awayTeamExternalId, Score fullTime,
                            Score halfTime, MatchWinner winner) {
    public MatchSnapshot {
        externalId = Match.requireExternalId(externalId);
        Match.requireUtcDate(utcDate);
        homeTeamExternalId = requireTeamExternalId(homeTeamExternalId, Match.HOME_TEAM_REQUIRED);
        awayTeamExternalId = requireTeamExternalId(awayTeamExternalId, Match.AWAY_TEAM_REQUIRED);
        Match.requireDifferentTeams(homeTeamExternalId, awayTeamExternalId);
    }

    public Match toNewMatch(Season season, Team home, Team away) {
        return new Match(null, externalId, season, utcDate, matchday, status, home, away, fullTime, halfTime, winner);
    }

    private static String requireTeamExternalId(String teamExternalId, String message) {
        if (teamExternalId == null || teamExternalId.isBlank()) {
            throw new CatalogInvariantException(message);
        }
        return teamExternalId.trim();
    }
}
