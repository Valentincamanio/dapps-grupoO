package ar.edu.unq.desapp.futbolmarket.persistence.mapper.match;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.season.SeasonMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.team.TeamMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match.MatchSQL;
import org.springframework.stereotype.Component;

/**
 * Además de traducir campo a campo, reparte cada {@link Score} en dos columnas y lo rearma: dos
 * columnas en {@code null} son un partido sin resultado, no un resultado vacío.
 */
@Component
public class MatchMapper {
    private final SeasonMapper seasonMapper;
    private final TeamMapper teamMapper;

    public MatchMapper(SeasonMapper seasonMapper, TeamMapper teamMapper) {
        this.seasonMapper = seasonMapper;
        this.teamMapper = teamMapper;
    }

    public Match toDomain(MatchSQL match) {
        return new Match(
                match.getId(),
                match.getExternalId(),
                seasonMapper.toDomain(match.getSeason()),
                match.getUtcDate(),
                match.getMatchday(),
                MatchStatus.valueOf(match.getStatus()),
                teamMapper.toDomain(match.getHomeTeam()),
                teamMapper.toDomain(match.getAwayTeam()),
                toScore(match.getFullTimeHome(), match.getFullTimeAway()),
                toScore(match.getHalfTimeHome(), match.getHalfTimeAway()),
                match.getWinner() == null ? null : MatchWinner.valueOf(match.getWinner())
        );
    }

    public MatchSQL toSQL(Match match) {
        return new MatchSQL(
                match.id(),
                match.externalId(),
                seasonMapper.toSQL(match.season()),
                match.utcDate(),
                match.matchday(),
                match.status().name(),
                teamMapper.toSQL(match.homeTeam()),
                teamMapper.toSQL(match.awayTeam()),
                homeGoals(match.fullTime()),
                awayGoals(match.fullTime()),
                homeGoals(match.halfTime()),
                awayGoals(match.halfTime()),
                match.winner() == null ? null : match.winner().name()
        );
    }

    private static Score toScore(Integer home, Integer away) {
        return home == null && away == null ? null : new Score(home, away);
    }

    private static Integer homeGoals(Score score) {
        return score == null ? null : score.home();
    }

    private static Integer awayGoals(Score score) {
        return score == null ? null : score.away();
    }
}
