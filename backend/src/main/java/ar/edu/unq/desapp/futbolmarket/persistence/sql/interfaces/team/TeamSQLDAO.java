package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TeamSQLDAO extends JpaRepository<TeamSQL, Long> {
    List<TeamSQL> findAllByExternalIdIn(Collection<String> externalIds);
}
