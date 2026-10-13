package ar.edu.unq.desapp.futbolmarket.persistence.repository.season;

import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.season.SeasonMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.season.SeasonSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class SeasonRepository {
    private final SeasonSQLDAO seasonDAO;
    private final SeasonMapper seasonMapper;

    public SeasonRepository(SeasonSQLDAO seasonDAO, SeasonMapper seasonMapper) {
        this.seasonDAO = seasonDAO;
        this.seasonMapper = seasonMapper;
    }

    @Transactional(readOnly = true)
    public Optional<Season> findByExternalId(String externalId) {
        return seasonDAO.findByExternalId(externalId).map(seasonMapper::toDomain);
    }

    @Transactional
    public Season save(Season season) {
        SeasonSQL savedSeason = seasonDAO.save(seasonMapper.toSQL(season));
        return seasonMapper.toDomain(savedSeason);
    }
}
