package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionMatchesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionTeamsDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.MatchDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.MatchTeamDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.PersonDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.ScoreDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.ScoreLineDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.SeasonDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.TeamDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Traduce el JSON de la fuente al modelo, campo a campo, y no decide nada de negocio (research
 * D7): un valor que no conoce queda en {@code null} y el modelo decide qué hacer con él.
 *
 * <p>Un dato que viola un invariante del modelo, como un id que falta, termina en una
 * {@code CatalogInvariantException} al construir el snapshot; el adapter la traduce a un error de
 * formato.</p>
 */
@Component
public class FootballDataMapper {

    public LeagueSnapshot toLeagueSnapshot(League league, CompetitionTeamsDto teams, CompetitionMatchesDto matches) {
        return new LeagueSnapshot(
                league,
                toSeason(league, teams.season()),
                listOf(teams.teams()).stream().map(this::toTeam).toList(),
                listOf(matches.matches()).stream().map(this::toMatch).toList()
        );
    }

    private Season toSeason(League league, SeasonDto season) {
        if (season == null) {
            return null;
        }
        return new Season(null, idOf(season.id()), league, season.startDate(), season.endDate(),
                season.currentMatchday());
    }

    private TeamSnapshot toTeam(TeamDto team) {
        return new TeamSnapshot(idOf(team.id()), team.name(), team.crest(),
                listOf(team.squad()).stream().map(this::toPlayer).toList());
    }

    private PlayerSnapshot toPlayer(PersonDto person) {
        return new PlayerSnapshot(idOf(person.id()), person.name(), toPosition(person.position()),
                person.dateOfBirth(), person.nationality());
    }

    private MatchSnapshot toMatch(MatchDto match) {
        ScoreDto score = match.score();
        return new MatchSnapshot(
                idOf(match.id()),
                match.season() == null ? null : idOf(match.season().id()),
                match.utcDate(),
                match.matchday(),
                enumOrNull(MatchStatus.class, match.status()),
                idOf(teamId(match.homeTeam())),
                idOf(teamId(match.awayTeam())),
                score == null ? null : toScore(score.fullTime()),
                score == null ? null : toScore(score.halfTime()),
                score == null ? null : enumOrNull(MatchWinner.class, score.winner())
        );
    }

    /**
     * La fuente solo informa las cuatro posiciones generales. Cualquier otro valor queda sin
     * posición (FR-011).
     */
    private static Position toPosition(String position) {
        return switch (position) {
            case "Goalkeeper" -> Position.GOALKEEPER;
            case "Defence" -> Position.DEFENDER;
            case "Midfield" -> Position.MIDFIELDER;
            case "Offence" -> Position.FORWARD;
            case null, default -> null;
        };
    }

    /**
     * Un resultado con los dos valores en {@code null} es un partido sin jugar, no un resultado vacío.
     */
    private static Score toScore(ScoreLineDto line) {
        if (line == null || (line.home() == null && line.away() == null)) {
            return null;
        }
        return new Score(line.home(), line.away());
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElse(null);
    }

    private static Long teamId(MatchTeamDto team) {
        return team == null ? null : team.id();
    }

    /**
     * Un id que falta queda en {@code null}, nunca en la cadena {@code "null"}, para que el
     * invariante del modelo lo rechace.
     */
    private static String idOf(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private static <T> List<T> listOf(List<T> list) {
        return list == null ? List.of() : list;
    }
}
