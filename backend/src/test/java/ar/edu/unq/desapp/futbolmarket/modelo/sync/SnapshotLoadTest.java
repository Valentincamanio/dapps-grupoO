package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.CASEMIRO;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LEAGUE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.players;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.row;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.statsEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * El snapshot de {@link StatsFixtures#snapshot()} cruza al Everton y al Manchester United, sus dos
 * partidos (el de la fecha 7 todavía sin jugar) y trae las estadísticas de Pickford y Casemiro en el
 * Everton - Manchester United de la fecha 1, el 560603.
 */
class SnapshotLoadTest {

    private static final String EVERTON_MAN_UTD_ID = "560603";
    private static final Season PREVIOUS_SEASON =
            new Season(2L, "2401", LEAGUE, LocalDate.of(2025, 8, 15), LocalDate.of(2026, 5, 24), 38);

    private final CatalogPlayers catalog = CatalogPlayers.of(players());

    @Test
    void cruzaEquiposPartidosYJugadoresConIdsLibresTambienDePartidosFuturos() {
        SnapshotLoad load = load(snapshot(), seasonMatches(), teams());

        List<Team> teamCrosses = load.teamCrosses();
        List<Match> matchCrosses = load.matchCrosses();
        List<MatchStatsLoad> loads = load.loads();

        assertThat(teamCrosses).containsExactly(crossed(EVERTON), crossed(MAN_UTD));
        assertThat(matchCrosses).extracting(Match::externalId, Match::whoscoredId, Match::status)
                .containsExactly(tuple(EVERTON_MAN_UTD_ID, "1983603", MatchStatus.FINISHED),
                        tuple("560621", "1983621", MatchStatus.TIMED));
        assertThat(loads).singleElement().satisfies(loaded -> {
            assertThat(loaded.source()).isEqualTo(LoadSource.SNAPSHOT);
            assertThat(loaded.match().statsLoaded()).isTrue();
            assertThat(loaded.match().whoscoredId()).isEqualTo("1983603");
            assertThat(loaded.crossedPlayers()).containsExactly(CASEMIRO.crossWith("75577"));
            assertThat(loaded.stats()).extracting(PlayerMatchStats::player)
                    .containsExactly(PICKFORD, CASEMIRO.crossWith("75577"));
        });
        assertThat(load.skipped()).isEmpty();
    }

    @Test
    void salteaUnEquipoQueNoEstaEnElCatalogo() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(new SnapshotTeamEntry(LEAGUE, "9999", "99")), List.of(),
                List.of());

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.TEAM_NOT_IN_CATALOG, 1));
    }

    @Test
    void salteaLosPartidosQueNoEstanEnElCatalogo() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(),
                List.of(new SnapshotMatchEntry(LEAGUE, "999999", "1999999")),
                List.of(new SnapshotStatsEntry(LEAGUE, "999998", List.of(row(PICKFORD, "93145", 90)))));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.MATCH_NOT_IN_CATALOG, 2));
        assertThat(load.loads()).isEmpty();
    }

    @Test
    void salteaLaFilaDeUnJugadorQueNoEstaEnElCatalogoYCargaElRestoDelPartido() {
        Player unknown = new Player(9L, "77777", "Jugador Desconocido", PICKFORD.position(), EVERTON);
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "93145", 96), row(unknown, "88888", 30))));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.PLAYER_NOT_IN_CATALOG, 1));
        assertThat(load.loads()).singleElement()
                .satisfies(loaded -> assertThat(loaded.stats()).extracting(PlayerMatchStats::player)
                        .containsExactly(PICKFORD));
    }

    @Test
    void salteaLasEstadisticasDeUnPartidoQueTodaviaNoTermino() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(),
                List.of(statsEntry(MAN_UTD, EVERTON, row(PICKFORD, "93145", 90))));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.MATCH_NOT_FINISHED, 1));
        assertThat(load.loads()).isEmpty();
    }

    @Test
    void anteUnCruceEnConflictoDeUnEquipoGanaLaBase() {
        List<Team> catalogTeams = replace(teams(), crossed(EVERTON));
        StatsSnapshot snapshot = new StatsSnapshot(List.of(
                new SnapshotTeamEntry(LEAGUE, EVERTON.externalId(), "77"),
                new SnapshotTeamEntry(LEAGUE, BRENTFORD.externalId(), "31")), List.of(), List.of());

        SnapshotLoad load = load(snapshot, seasonMatches(catalogTeams), catalogTeams);

        assertThat(load.teamCrosses()).isEmpty();
        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.CONFLICTING_CROSS, 2));
    }

    @Test
    void anteUnCruceEnConflictoDeUnJugadorGanaLaBaseYSusEstadisticasSeCarganIgual() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "11111", 96), row(CASEMIRO, "93145", 62))));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.CONFLICTING_CROSS, 2));
        assertThat(load.loads()).singleElement().satisfies(loaded -> {
            assertThat(loaded.crossedPlayers()).isEmpty();
            assertThat(loaded.stats()).extracting(PlayerMatchStats::player).containsExactly(PICKFORD, CASEMIRO);
        });
    }

    @Test
    void unPartidoQueYaTieneEstadisticasConservaLasSuyasSinContarse() {
        List<Match> matches = seasonMatches().stream()
                .map(match -> match.externalId().equals(EVERTON_MAN_UTD_ID) ? match.markStatsLoaded() : match)
                .toList();
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "93145", 96))));

        SnapshotLoad load = load(snapshot, matches, teams());

        assertThat(load.loads()).isEmpty();
        assertThat(load.skipped()).isEmpty();
    }

    @Test
    void loDeOtraTemporadaYUnEquipoQueDejoLaLigaSeIgnoranSinContarse() {
        Team leicester = new Team(70L, "338", "Leicester City FC", null, LEAGUE);
        Match previousSeasonMatch = new Match(70L, "540100", PREVIOUS_SEASON, Instant.parse("2026-05-10T14:00:00Z"),
                37, MatchStatus.FINISHED, leicester, EVERTON, null, null, null);
        StatsSnapshot snapshot = new StatsSnapshot(
                List.of(new SnapshotTeamEntry(LEAGUE, "338", "14")),
                List.of(new SnapshotMatchEntry(LEAGUE, "540100", "1900100")),
                List.of(new SnapshotStatsEntry(LEAGUE, "540100", List.of(row(PICKFORD, "93145", 90)))));
        CatalogReferences referenced = new CatalogReferences(
                Stream.concat(teams().stream(), Stream.of(leicester)).toList(),
                Stream.concat(seasonMatches().stream(), Stream.of(previousSeasonMatch)).toList());

        SnapshotLoad load = new SnapshotLoad(snapshot, seasonMatches(), teams(), catalog, referenced);

        assertThat(load.teamCrosses()).isEmpty();
        assertThat(load.matchCrosses()).isEmpty();
        assertThat(load.loads()).isEmpty();
        assertThat(load.skipped()).isEmpty();
    }

    @Test
    void unaFilaSinIdDeWhoScoredSeCargaSinCruzarANadie() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(),
                List.of(statsEntry(EVERTON, MAN_UTD, row(CASEMIRO, null, 62))));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.skipped()).isEmpty();
        assertThat(load.loads()).singleElement().satisfies(loaded -> {
            assertThat(loaded.crossedPlayers()).isEmpty();
            assertThat(loaded.stats()).extracting(PlayerMatchStats::player).containsExactly(CASEMIRO);
        });
    }

    @Test
    void unPartidoSinFilasSeCargaIgual() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(), List.of(statsEntry(EVERTON, MAN_UTD)));

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.loads()).singleElement().satisfies(loaded -> {
            assertThat(loaded.match().statsLoaded()).isTrue();
            assertThat(loaded.stats()).isEmpty();
        });
    }

    @Test
    void unIdAsignadoEnLaMismaCargaCuentaComoOcupado() {
        StatsSnapshot snapshot = new StatsSnapshot(List.of(
                new SnapshotTeamEntry(LEAGUE, EVERTON.externalId(), "31"),
                new SnapshotTeamEntry(LEAGUE, MAN_UTD.externalId(), "31")),
                List.of(matchEntry(EVERTON, MAN_UTD)), List.of());

        SnapshotLoad load = load(snapshot, seasonMatches(), teams());

        assertThat(load.teamCrosses()).containsExactly(crossed(EVERTON));
        assertThat(load.skipped()).containsExactly(Map.entry(SnapshotSkipReason.CONFLICTING_CROSS, 1));
    }

    private SnapshotLoad load(StatsSnapshot snapshot, List<Match> matches, List<Team> catalogTeams) {
        return new SnapshotLoad(snapshot, matches, catalogTeams, catalog, new CatalogReferences(catalogTeams, matches));
    }

    private static List<Team> replace(List<Team> teams, Team version) {
        return teams.stream()
                .map(team -> team.externalId().equals(version.externalId()) ? version : team)
                .toList();
    }
}
