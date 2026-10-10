package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionMatchesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionTeamsDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.MatchDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.MatchTeamDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.PersonDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.ScoreDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.ScoreLineDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.SeasonDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.TeamDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

class FootballDataMapperTest {

    private static final SeasonDto SEASON = new SeasonDto(2502L, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 6);
    private static final String LIVERPOOL_CREST = "https://crests.football-data.org/64.png";
    private static final CompetitionMatchesDto NO_MATCHES = new CompetitionMatchesDto(List.of());
    private static final ScoreLineDto NOT_PLAYED = new ScoreLineDto(null, null);

    private final FootballDataMapper mapper = new FootballDataMapper();

    @ParameterizedTest
    @CsvSource({"Goalkeeper, GOALKEEPER", "Defence, DEFENDER", "Midfield, MIDFIELDER", "Offence, FORWARD"})
    void traduceLasCuatroPosicionesDeLaFuente(String sourcePosition, Position expected) {
        CompetitionTeamsDto teams = teamsWith(person(1795L, sourcePosition));

        PlayerSnapshot player = firstPlayer(mapper.toLeagueSnapshot(League.PREMIER, teams, NO_MATCHES));

        assertThat(player.position()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Attacker", "goalkeeper", ""})
    void unaPosicionNulaODesconocidaQuedaSinPosicion(String sourcePosition) {
        CompetitionTeamsDto teams = teamsWith(person(301113L, sourcePosition));

        PlayerSnapshot player = firstPlayer(mapper.toLeagueSnapshot(League.PREMIER, teams, NO_MATCHES));

        assertThat(player.position()).isNull();
    }

    @ParameterizedTest
    @EnumSource(MatchStatus.class)
    void traduceLosOnceEstadosDeLaFuente(MatchStatus status) {
        CompetitionMatchesDto matches = matchesWith(match(status.name(), null));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"LIVE", "finished"})
    void unEstadoNuloODesconocidoQuedaEnNull(String sourceStatus) {
        CompetitionMatchesDto matches = matchesWith(match(sourceStatus, null));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.status()).isNull();
    }

    @ParameterizedTest
    @EnumSource(MatchWinner.class)
    void traduceLosTresGanadoresDeLaFuente(MatchWinner winner) {
        CompetitionMatchesDto matches = matchesWith(match("FINISHED", new ScoreDto(winner.name(), null, null)));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.winner()).isEqualTo(winner);
    }

    @Test
    void unGanadorNuloQuedaEnNull() {
        CompetitionMatchesDto matches = matchesWith(match("TIMED", new ScoreDto(null, NOT_PLAYED, NOT_PLAYED)));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.winner()).isNull();
    }

    @Test
    void unResultadoConLocalYVisitanteEnNullQuedaSinResultado() {
        CompetitionMatchesDto matches = matchesWith(match("TIMED", new ScoreDto(null, NOT_PLAYED, NOT_PLAYED)));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.fullTime()).isNull();
        assertThat(match.halfTime()).isNull();
    }

    @Test
    void unPartidoJugadoTraeSuResultadoFinalYElDelPrimerTiempo() {
        ScoreDto score = new ScoreDto("HOME_TEAM", new ScoreLineDto(3, 0), new ScoreLineDto(2, 0));
        CompetitionMatchesDto matches = matchesWith(match("FINISHED", score));

        MatchSnapshot match = firstMatch(mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches));

        assertThat(match.fullTime()).isEqualTo(new Score(3, 0));
        assertThat(match.halfTime()).isEqualTo(new Score(2, 0));
    }

    @Test
    void losIdsDeLaFuentePasanATexto() {
        CompetitionMatchesDto matches = matchesWith(match("FINISHED", null));

        LeagueSnapshot snapshot = mapper.toLeagueSnapshot(League.PREMIER, defaultTeams(), matches);
        MatchSnapshot match = firstMatch(snapshot);

        assertThat(snapshot.season().externalId()).isEqualTo("2502");
        assertThat(snapshot.teams().getFirst().externalId()).isEqualTo("64");
        assertThat(firstPlayer(snapshot).externalId()).isEqualTo("1795");
        assertThat(match.externalId()).isEqualTo("560542");
        assertThat(match.seasonExternalId()).isEqualTo("2502");
        assertThat(match.homeTeamExternalId()).isEqualTo("57");
        assertThat(match.awayTeamExternalId()).isEqualTo("1076");
    }

    @Test
    void unIdQueFaltaNoSeConvierteEnLaCadenaNull() {
        CompetitionTeamsDto teams = teamsWith(person(null, "Goalkeeper"));

        assertThatThrownBy(() -> mapper.toLeagueSnapshot(League.PREMIER, teams, NO_MATCHES))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El identificador externo del jugador es obligatorio.");
    }

    @Test
    void tomaElNombreOficialYElEscudoDelEquipo() {
        CompetitionTeamsDto teams = defaultTeams();

        TeamSnapshot team = mapper.toLeagueSnapshot(League.PREMIER, teams, NO_MATCHES).teams().getFirst();

        assertThat(team.name()).isEqualTo("Liverpool FC");
        assertThat(team.crest()).isEqualTo(LIVERPOOL_CREST);
    }

    @Test
    void lasListasNulasQuedanVacias() {
        CompetitionTeamsDto teams = new CompetitionTeamsDto(SEASON,
                List.of(new TeamDto(64L, "Liverpool FC", LIVERPOOL_CREST, null)));
        CompetitionMatchesDto matches = new CompetitionMatchesDto(null);

        LeagueSnapshot snapshot = mapper.toLeagueSnapshot(League.PREMIER, teams, matches);

        assertThat(snapshot.teams().getFirst().squad()).isEmpty();
        assertThat(snapshot.matches()).isEmpty();
    }

    private static CompetitionTeamsDto defaultTeams() {
        return teamsWith(person(1795L, "Goalkeeper"));
    }

    private static CompetitionTeamsDto teamsWith(PersonDto... squad) {
        return new CompetitionTeamsDto(SEASON, List.of(new TeamDto(64L, "Liverpool FC", LIVERPOOL_CREST, List.of(squad))));
    }

    private static PersonDto person(Long id, String position) {
        return new PersonDto(id, "Alisson Becker", position, LocalDate.of(1992, 10, 2), "Brazil");
    }

    private static CompetitionMatchesDto matchesWith(MatchDto... matches) {
        return new CompetitionMatchesDto(List.of(matches));
    }

    private static MatchDto match(String status, ScoreDto score) {
        return new MatchDto(560542L, SEASON, Instant.parse("2026-08-21T19:00:00Z"), status, 1,
                new MatchTeamDto(57L), new MatchTeamDto(1076L), score);
    }

    private static PlayerSnapshot firstPlayer(LeagueSnapshot snapshot) {
        return snapshot.teams().getFirst().squad().getFirst();
    }

    private static MatchSnapshot firstMatch(LeagueSnapshot snapshot) {
        return snapshot.matches().getFirst();
    }
}
