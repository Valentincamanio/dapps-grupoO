package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_WS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LEAGUE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD_WS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.fixtures;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.fixturesOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teams;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class TeamCrossingTest {

    private static final int RESCHEDULE_DAYS = 10;
    private static final int UNALIGNABLE_DAYS = 3;

    /**
     * Un equipo del catálogo que juega los mismos días y con la misma localía que el Manchester
     * United, pero contra cinco equipos que no juegan con nadie más. Mientras sus rivales de
     * WhoScored no estén cruzados, su calendario coincide con el de "Man Utd".
     */
    private static final Team TWIN = new Team(90L, "9000", "Red Devils FC", null, LEAGUE);

    @Test
    void cruzaPorNombreCuandoElCalendarioLoConfirma() {
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(EVERTON_WS), fixturesOf(EVERTON_WS));

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).containsExactly(crossed(EVERTON));
    }

    @Test
    void cruzaSoloPorCalendarioAUnEquipoConOtroNombre() {
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(MAN_UTD_WS), fixturesOf(MAN_UTD_WS));

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).containsExactly(MAN_UTD.crossWith("32"));
    }

    @Test
    void dosEquiposDelCatalogoCuyoCalendarioCoincideQuedanAmbiguos() {
        TeamCrossing crossing = TeamCrossing.of(withTwin(teams()), withTwinMatches(seasonMatches()),
                List.of(MAN_UTD_WS), fixturesOf(MAN_UTD_WS));

        List<Team> crossedTeams = crossing.crossed();
        List<UncrossedTeam> uncrossed = crossing.uncrossed();

        assertThat(crossedTeams).isEmpty();
        assertThat(uncrossed).contains(
                new UncrossedTeam(MAN_UTD.externalId(), MAN_UTD.name(), CrossFailure.AMBIGUOUS),
                new UncrossedTeam(TWIN.externalId(), TWIN.name(), CrossFailure.AMBIGUOUS));
    }

    @Test
    void sinNingunEquipoCuyoCalendarioCoincidaQuedaNoEncontrado() {
        List<ReportedFixture> moved = moved(fixturesOf(MAN_UTD_WS), UNALIGNABLE_DAYS, fixturesOf(MAN_UTD_WS).size());
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(MAN_UTD_WS), moved);

        List<UncrossedTeam> uncrossed = crossing.uncrossed();

        assertThat(crossing.crossed()).isEmpty();
        assertThat(uncrossed).contains(new UncrossedTeam(MAN_UTD.externalId(), MAN_UTD.name(), CrossFailure.NOT_FOUND));
    }

    @Test
    void elNombreCoincidePeroElCalendarioNoQuedaSinConfirmar() {
        List<ReportedFixture> moved = moved(fixturesOf(EVERTON_WS), UNALIGNABLE_DAYS, fixturesOf(EVERTON_WS).size());
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(EVERTON_WS), moved);

        List<UncrossedTeam> uncrossed = crossing.uncrossed();

        assertThat(crossing.crossed()).isEmpty();
        assertThat(uncrossed)
                .contains(new UncrossedTeam(EVERTON.externalId(), EVERTON.name(), CrossFailure.NOT_CONFIRMED));
    }

    @Test
    void toleraDosFechasReprogramadas() {
        List<ReportedFixture> rescheduled = moved(fixturesOf(MAN_UTD_WS), RESCHEDULE_DAYS, 2);
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(MAN_UTD_WS), rescheduled);

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).containsExactly(MAN_UTD.crossWith("32"));
    }

    @Test
    void noToleraTresFechasReprogramadas() {
        List<ReportedFixture> rescheduled = moved(fixturesOf(MAN_UTD_WS), RESCHEDULE_DAYS, 3);
        TeamCrossing crossing = TeamCrossing.of(teams(), seasonMatches(), List.of(MAN_UTD_WS), rescheduled);

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).isEmpty();
    }

    @Test
    void unRivalQueContradiceUnCruceGuardadoImpideElCruce() {
        List<Team> catalog = replace(teams(), EVERTON.crossWith("999"));
        TeamCrossing crossing = TeamCrossing.of(catalog, seasonMatches(catalog), List.of(MAN_UTD_WS),
                fixturesOf(MAN_UTD_WS));

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).isEmpty();
    }

    @Test
    void unEquipoYaCruzadoNoSeEvaluaAunqueSuIdSeaOtro() {
        List<Team> catalog = replace(teams(), MAN_UTD.crossWith("77"));
        TeamCrossing crossing = TeamCrossing.of(catalog, seasonMatches(catalog), List.of(MAN_UTD_WS),
                fixturesOf(MAN_UTD_WS));

        List<Team> crossedTeams = crossing.crossed();
        List<UncrossedTeam> uncrossed = crossing.uncrossed();

        assertThat(crossedTeams).isEmpty();
        assertThat(uncrossed).extracting(UncrossedTeam::teamExternalId).doesNotContain(MAN_UTD.externalId());
    }

    @Test
    void unCruceHabilitaElDeUnRivalEnLaVueltaSiguiente() {
        TeamCrossing crossing = TeamCrossing.of(withTwin(teams()), withTwinMatches(seasonMatches()), sourceTeams(),
                fixtures());

        List<Team> crossedTeams = crossing.crossed();

        assertThat(crossedTeams).containsExactly(crossed(EVERTON), crossed(BRENTFORD), crossed(TOTTENHAM),
                crossed(ARSENAL), crossed(LIVERPOOL), crossed(MAN_UTD));
    }

    /**
     * Mueve las primeras {@code count} filas esos días: más de uno, y ya no se alinean.
     */
    private static List<ReportedFixture> moved(List<ReportedFixture> fixtures, int days, int count) {
        List<ReportedFixture> moved = new ArrayList<>(fixtures);
        for (int index = 0; index < count; index++) {
            ReportedFixture fixture = fixtures.get(index);
            moved.set(index, new ReportedFixture(fixture.whoscoredId(), fixture.date().plusDays(days),
                    fixture.homeTeamWhoscoredId(), fixture.homeTeamName(), fixture.awayTeamWhoscoredId(),
                    fixture.awayTeamName(), fixture.finished()));
        }
        return moved;
    }

    private static List<Team> replace(List<Team> teams, Team version) {
        return teams.stream()
                .map(team -> team.externalId().equals(version.externalId()) ? version : team)
                .toList();
    }

    private static List<Team> withTwin(List<Team> teams) {
        return Stream.concat(teams.stream(), Stream.concat(Stream.of(TWIN), ghosts().stream())).toList();
    }

    /**
     * Los partidos del gemelo: cada partido del Manchester United, el mismo día y con la misma
     * localía, contra el fantasma de su rival.
     */
    private static List<Match> withTwinMatches(List<Match> matches) {
        List<Team> ghosts = ghosts();
        List<Match> twinMatches = new ArrayList<>();
        for (Match match : matches) {
            boolean home = match.homeTeam().externalId().equals(MAN_UTD.externalId());
            boolean away = match.awayTeam().externalId().equals(MAN_UTD.externalId());
            if (home || away) {
                Team rival = home ? match.awayTeam() : match.homeTeam();
                Team ghost = ghosts.get(teams().indexOf(rival) - 1);
                twinMatches.add(new Match(null, "9" + match.externalId(), match.season(), match.utcDate(),
                        match.matchday(), match.status(), home ? TWIN : ghost, home ? ghost : TWIN, null, null, null));
            }
        }
        return Stream.concat(matches.stream(), twinMatches.stream()).toList();
    }

    /**
     * Un fantasma por cada rival del Manchester United, en el orden de {@link StatsFixtures#teams()}.
     */
    private static List<Team> ghosts() {
        return List.of(
                new Team(91L, "9001", "Rovers Everton", null, LEAGUE),
                new Team(92L, "9002", "Rovers Brentford", null, LEAGUE),
                new Team(93L, "9003", "Rovers Tottenham", null, LEAGUE),
                new Team(94L, "9004", "Rovers Arsenal", null, LEAGUE),
                new Team(95L, "9005", "Rovers Liverpool", null, LEAGUE));
    }
}
