package ar.edu.unq.desapp.futbolmarket.persistence.repository.match;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
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

    /**
     * Los partidos de una temporada, con su temporada y sus dos equipos.
     */
    @Transactional(readOnly = true)
    public List<Match> findAllBySeason(String seasonExternalId) {
        return toMatches(matchDAO.findAllBySeasonExternalId(seasonExternalId));
    }

    /**
     * Los partidos ya cruzados con WhoScored, de cualquier temporada, que van al snapshot.
     */
    @Transactional(readOnly = true)
    public List<Match> findAllCrossed() {
        return toMatches(matchDAO.findAllByWhoscoredIdIsNotNull());
    }

    /**
     * Los partidos que ya tienen estadísticas, que van al snapshot.
     */
    @Transactional(readOnly = true)
    public List<Match> findAllWithStats() {
        return toMatches(matchDAO.findAllByStatsLoadedTrue());
    }

    /**
     * Los partidos terminados, de cualquier temporada, con su temporada y sus dos equipos.
     */
    @Transactional(readOnly = true)
    public List<Match> findAllFinished() {
        return toMatches(matchDAO.findAllByStatus(MatchStatus.FINISHED.name()));
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
