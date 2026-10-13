package ar.edu.unq.desapp.futbolmarket.persistence.mapper.team;

import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {
    public Team toDomain(TeamSQL team) {
        return new Team(team.getId(), team.getExternalId(), team.getName(), team.getCrest(), team.getLeague());
    }

    public TeamSQL toSQL(Team team) {
        return new TeamSQL(team.id(), team.externalId(), team.name(), team.crest(), team.league());
    }
}
