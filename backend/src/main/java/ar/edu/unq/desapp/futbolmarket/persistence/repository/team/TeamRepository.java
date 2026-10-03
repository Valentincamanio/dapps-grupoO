package ar.edu.unq.desapp.futbolmarket.persistence.repository.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.team.TeamMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TeamRepository {
    private final TeamSQLDAO teamDAO;
    private final TeamMapper teamMapper;

    public TeamRepository(TeamSQLDAO teamDAO, TeamMapper teamMapper) {
        this.teamDAO = teamDAO;
        this.teamMapper = teamMapper;
    }

    @Transactional
    public Team findOrCreate(String name, League league) {
        return teamDAO.findByNameAndLeague(name, league)
                .map(teamMapper::toDomain)
                .orElseGet(() -> save(new Team(name, league)));
    }

    @Transactional
    public Team save(Team team) {
        TeamSQL savedTeam = teamDAO.save(teamMapper.toSQL(team));
        return teamMapper.toDomain(savedTeam);
    }
}
