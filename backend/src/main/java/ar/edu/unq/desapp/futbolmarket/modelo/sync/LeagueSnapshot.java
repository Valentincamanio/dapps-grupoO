package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.List;
import java.util.stream.Stream;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Todo lo que la fuente informó de una liga en una descarga: la temporada en curso, los equipos
 * con sus planteles y los partidos.
 *
 * <p>Sus invariantes son errores de la fuente y no del catálogo, así que se lanzan como
 * {@link ExternalSourceException}: la liga falla sin escribir nada y las demás siguen (FR-034).</p>
 */
public record LeagueSnapshot(League league, Season season, List<TeamSnapshot> teams, List<MatchSnapshot> matches) {
    public LeagueSnapshot {
        teams = teams == null ? List.of() : List.copyOf(teams);
        matches = matches == null ? List.of() : List.copyOf(matches);
        if (teams.isEmpty()) {
            throw new ExternalSourceException(ExternalSourceException.NO_TEAMS);
        }
        if (season == null || season.league() != league) {
            throw new ExternalSourceException(ExternalSourceException.INVALID_FORMAT);
        }
        if (matches.stream().anyMatch(match -> belongsToOtherSeason(match, season))) {
            throw new ExternalSourceException(ExternalSourceException.MATCHES_FROM_OTHER_SEASON);
        }
    }

    public List<String> teamExternalIds() {
        return teams.stream().map(TeamSnapshot::externalId).distinct().toList();
    }

    public List<String> playerExternalIds() {
        return teams.stream()
                .flatMap(team -> team.squad().stream())
                .map(PlayerSnapshot::externalId)
                .distinct()
                .toList();
    }

    public List<String> matchExternalIds() {
        return matches.stream().map(MatchSnapshot::externalId).distinct().toList();
    }

    /**
     * Los equipos que referencian los partidos, local y visitante. Pueden no ser los de la liga.
     */
    public List<String> matchTeamExternalIds() {
        return matches.stream()
                .flatMap(match -> Stream.of(match.homeTeamExternalId(), match.awayTeamExternalId()))
                .distinct()
                .toList();
    }

    /**
     * Un partido sin temporada informada se acepta: solo se rechaza el que dice ser de otra.
     */
    private static boolean belongsToOtherSeason(MatchSnapshot match, Season season) {
        return match.seasonExternalId() != null && !match.seasonExternalId().equals(season.externalId());
    }
}
