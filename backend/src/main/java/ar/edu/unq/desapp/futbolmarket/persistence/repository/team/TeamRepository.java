package ar.edu.unq.desapp.futbolmarket.persistence.repository.team;

import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.team.TeamMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public class TeamRepository {
    private final TeamSQLDAO teamDAO;
    private final TeamMapper teamMapper;

    public TeamRepository(TeamSQLDAO teamDAO, TeamMapper teamMapper) {
        this.teamDAO = teamDAO;
        this.teamMapper = teamMapper;
    }

    /**
     * Carga en una sola consulta los equipos guardados. Sin ids no hay nada que buscar, así que
     * no se consulta la base.
     */
    @Transactional(readOnly = true)
    public List<Team> findAllByExternalIds(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return List.of();
        }
        return toTeams(teamDAO.findAllByExternalIdIn(externalIds));
    }

    @Transactional
    public List<Team> saveAll(List<Team> teams) {
        List<TeamSQL> savedTeams = teamDAO.saveAll(teams.stream().map(teamMapper::toSQL).toList());
        return toTeams(savedTeams);
    }

    @Transactional
    public Team save(Team team) {
        TeamSQL savedTeam = teamDAO.save(teamMapper.toSQL(team));
        return teamMapper.toDomain(savedTeam);
    }

    private List<Team> toTeams(List<TeamSQL> teams) {
        return teams.stream().map(teamMapper::toDomain).toList();
    }
}
