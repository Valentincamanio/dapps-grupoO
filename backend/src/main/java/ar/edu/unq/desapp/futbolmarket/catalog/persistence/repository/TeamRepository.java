package ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.mapper.TeamMapper;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.TeamSQL;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.TeamSQLDAO;
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
