package ar.edu.unq.desapp.futbolmarket.persistence.repository.match;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.match.MatchMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match.MatchSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public class MatchRepository {
    private final MatchSQLDAO matchDAO;
    private final MatchMapper matchMapper;

    public MatchRepository(MatchSQLDAO matchDAO, MatchMapper matchMapper) {
        this.matchDAO = matchDAO;
        this.matchMapper = matchMapper;
    }

    /**
     * Carga en una sola consulta los partidos guardados, con su temporada y sus dos equipos. Sin
     * ids no hay nada que buscar, así que no se consulta la base.
     */
    @Transactional(readOnly = true)
    public List<Match> findAllByExternalIds(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return List.of();
        }
        return toMatches(matchDAO.findAllByExternalIdIn(externalIds));
    }

    @Transactional
    public List<Match> saveAll(List<Match> matches) {
        List<MatchSQL> savedMatches = matchDAO.saveAll(matches.stream().map(matchMapper::toSQL).toList());
        return toMatches(savedMatches);
    }

    private List<Match> toMatches(List<MatchSQL> matches) {
        return matches.stream().map(matchMapper::toDomain).toList();
    }
}
