package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionMatchesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionTeamsDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;

/**
 * Lo único que el servicio conoce de Football-Data.org: recibe una liga y devuelve lo que la fuente
 * informa de ella, en términos del modelo. No persiste nada.
 */
@Component
public class FootballDataAdapter {
    private final FootballDataClient client;
    private final FootballDataMapper mapper;

    public FootballDataAdapter(FootballDataClient client, FootballDataMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    /**
     * Hace exactamente dos consultas, primero los equipos y después los partidos (FR-005). Un cuerpo
     * vacío o un dato que viola un invariante del modelo es un error de formato de la fuente.
     */
    public LeagueSnapshot fetchLeague(League league) {
        String code = FootballDataCompetition.of(league).code();
        CompetitionTeamsDto teams = requireBody(client.fetchTeams(code));
        CompetitionMatchesDto matches = requireBody(client.fetchMatches(code));
        try {
            return mapper.toLeagueSnapshot(league, teams, matches);
        } catch (CatalogInvariantException e) {
            throw new ExternalSourceException(ExternalSourceException.INVALID_FORMAT, e);
        }
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new ExternalSourceException(ExternalSourceException.INVALID_FORMAT);
        }
        return body;
    }
}
