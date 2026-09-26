package ar.edu.unq.desapp.futbolmarket.catalog.persistence.mapper;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.TeamSQL;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {
    public Team toDomain(TeamSQL team) {
        return new Team(team.getId(), team.getName(), team.getLeague());
    }

    public TeamSQL toSQL(Team team) {
        return new TeamSQL(team.id(), team.name(), team.league());
    }
}
