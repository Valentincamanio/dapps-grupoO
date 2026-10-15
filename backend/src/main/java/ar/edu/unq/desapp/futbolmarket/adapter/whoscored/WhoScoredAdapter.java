package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.FixtureRowDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchCentreDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentFixturesDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedFixture;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedTeam;

/**
 * Lo único que el servicio conoce de WhoScored: recibe y devuelve modelo, y los DTO no salen de
 * acá. Un bloqueo sale como {@code SourceBlockedException}; ninguna excepción del cliente HTTP sale.
 */
@Component
public class WhoScoredAdapter {
    private final WhoScoredClient client;
    private final TournamentFixturesExtractor tournamentFixturesExtractor;
    private final FixtureMatchesExtractor fixtureMatchesExtractor;
    private final MatchCentreExtractor matchCentreExtractor;
    private final WhoScoredMapper mapper;

    public WhoScoredAdapter(WhoScoredClient client, TournamentFixturesExtractor tournamentFixturesExtractor,
                            FixtureMatchesExtractor fixtureMatchesExtractor, MatchCentreExtractor matchCentreExtractor,
                            WhoScoredMapper mapper) {
        this.client = client;
        this.tournamentFixturesExtractor = tournamentFixturesExtractor;
        this.fixtureMatchesExtractor = fixtureMatchesExtractor;
        this.matchCentreExtractor = matchCentreExtractor;
        this.mapper = mapper;
    }

    /**
     * Los equipos de la fecha en curso de la liga, de su página.
     */
    public List<ReportedTeam> fetchLeagueTeams(League league) {
        TournamentFixturesDto tournamentFixtures =
                client.fetch(WhoScoredPages.league(league), tournamentFixturesExtractor::extract);
        return mapper.toTeams(tournamentFixtures, league);
    }

    /**
     * Las filas del torneo de la liga en la página de fixtures del equipo.
     */
    public List<ReportedFixture> fetchTeamFixtures(League league, String teamWhoscoredId) {
        List<FixtureRowDto> rows = client.fetch(WhoScoredPages.teamFixtures(teamWhoscoredId),
                fixtureMatchesExtractor::extract);
        return mapper.toFixtures(rows, league);
    }

    /**
     * Las estadísticas del partido. Vacío si la página todavía no tiene datos
     * ({@code matchCentreData: null}), que no es un rechazo.
     */
    public Optional<ReportedMatchStats> fetchMatchStats(String matchWhoscoredId) {
        Optional<MatchCentreDto> matchCentre =
                client.fetch(WhoScoredPages.match(matchWhoscoredId), matchCentreExtractor::extract);
        return matchCentre.map(data -> mapper.toMatchStats(matchWhoscoredId, data));
    }
}
