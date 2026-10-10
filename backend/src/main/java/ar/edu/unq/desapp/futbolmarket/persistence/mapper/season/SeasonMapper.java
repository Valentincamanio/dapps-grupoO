package ar.edu.unq.desapp.futbolmarket.persistence.mapper.season;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.season.SeasonSQL;
import org.springframework.stereotype.Component;

@Component
public class SeasonMapper {
    public Season toDomain(SeasonSQL season) {
        return new Season(
                season.getId(),
                season.getExternalId(),
                League.valueOf(season.getLeague()),
                season.getStartDate(),
                season.getEndDate(),
                season.getCurrentMatchday()
        );
    }

    public SeasonSQL toSQL(Season season) {
        return new SeasonSQL(
                season.id(),
                season.externalId(),
                season.league().name(),
                season.startDate(),
                season.endDate(),
                season.currentMatchday()
        );
    }
}
