package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_MAN_UTD_STATS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LEAGUE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD_WS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedSeasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.evertonManUtd;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.fixturesOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.players;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.whoscoredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Las fechas 0 a 6 de {@link StatsFixtures} están terminadas: son 21 partidos pendientes, del
 * 560600 al 560620, y cada equipo juega siete de ellos.
 */
class LeagueStatsSyncTest {

    private static final Season PREVIOUS_SEASON =
            new Season(2L, "2401", LEAGUE, LocalDate.of(2025, 8, 15), LocalDate.of(2026, 5, 24), 38);
    private static final String EVERTON_MAN_UTD_ID = "560603";

    private final CatalogPlayers catalog = CatalogPlayers.of(players());

    @Test
    void losPendientesSonLosTerminadosSinEstadisticasDeLaTemporadaEnCursoDelMasAntiguoAlMasReciente() {
        List<Match> matches = new ArrayList<>(seasonMatches().stream().map(LeagueStatsSyncTest::withSomeExceptions)
                .toList());
        matches.add(new Match(null, "540000", PREVIOUS_SEASON, Instant.parse("2026-05-24T15:00:00Z"), 38,
                MatchStatus.FINISHED, EVERTON, MAN_UTD, null, null, null));
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, matches.reversed());

        List<Match> pending = sync.pendingMatches();

        assertThat(pending).extracting(Match::externalId).containsExactlyElementsOf(
                Stream.iterate(560600, id -> id <= 560620, id -> id + 1)
                        .filter(id -> id != 560603 && id != 560604)
                        .map(String::valueOf)
                        .toList());
    }

    @Test
    void sinPendientesNoPideNingunaPagina() {
        List<Match> processed = seasonMatches().stream()
                .map(match -> match.status() == MatchStatus.FINISHED ? match.markStatsLoaded() : match)
                .toList();
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, processed);

        boolean needsLeaguePage = sync.needsLeaguePage();
        Optional<String> nextPage = sync.nextFixturesPage();

        assertThat(needsLeaguePage).isFalse();
        assertThat(nextPage).isEmpty();
        assertThat(sync.matchesToScrape()).isEmpty();
    }

    @Test
    void sinTemporadaEnElCatalogoNoHayPendientes() {
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, null, seasonMatches());

        List<Match> pending = sync.pendingMatches();

        assertThat(pending).isEmpty();
        assertThat(sync.needsLeaguePage()).isFalse();
    }

    @Test
    void conTodosLosEquiposCruzadosNoPideLaPaginaDeLaLiga() {
        LeagueStatsSync crossedSync = new LeagueStatsSync(LEAGUE, SEASON, crossedSeasonMatches());
        LeagueStatsSync uncrossedSync = new LeagueStatsSync(LEAGUE, SEASON, seasonMatches());

        boolean crossedNeedsLeaguePage = crossedSync.needsLeaguePage();
        boolean uncrossedNeedsLeaguePage = uncrossedSync.needsLeaguePage();

        assertThat(crossedNeedsLeaguePage).isFalse();
        assertThat(uncrossedNeedsLeaguePage).isTrue();
    }

    @Test
    void primeroPideLosEquiposDeWhoScoredSinCorrespondenciaEnElOrdenEnQueSeConocieronYNuncaRepite() {
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, seasonMatches());
        sync.registerSourceTeams(sourceTeams());

        Optional<String> first = sync.nextFixturesPage();
        sync.registerFixtures(MAN_UTD_WS.whoscoredId(), fixturesOf(MAN_UTD_WS));
        Optional<String> second = sync.nextFixturesPage();

        assertThat(first).contains("32");
        assertThat(second).contains("31");
    }

    /**
     * Con los partidos del Manchester United ya cruzados, el Everton, el Arsenal y el Tottenham
     * cubren seis pendientes sin id cada uno, y el Liverpool y el Brentford, cinco. El Everton
     * aparece antes en el calendario.
     */
    @Test
    void despuesPideElEquipoCruzadoQueCubreMasPendientesYAIgualCantidadElPrimeroDelCalendario() {
        List<Match> matches = crossedSeasonMatches().stream()
                .map(match -> involvesManUtd(match)
                        ? match.crossWith(whoscoredIdOf(match.homeTeam(), match.awayTeam()))
                        : match)
                .toList();
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, matches);

        Optional<String> first = sync.nextFixturesPage();
        sync.registerFixtures("31", List.of());
        Optional<String> second = sync.nextFixturesPage();
        sync.registerFixtures("13", List.of());
        Optional<String> third = sync.nextFixturesPage();

        assertThat(first).contains("31");
        assertThat(second).contains("13");
        assertThat(third).contains("30");
    }

    @Test
    void descartaLasFilasFueraDeLasFechasDeLaTemporada() {
        ReportedFixture previousSeason = new ReportedFixture("1900000", LocalDate.of(2026, 4, 11), "32", "Man Utd",
                "26", "Liverpool", true);
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, crossedSeasonMatches());

        sync.registerFixtures(MAN_UTD_WS.whoscoredId(),
                Stream.concat(Stream.of(previousSeason), fixturesOf(MAN_UTD_WS).stream()).toList());
        List<Match> crossedMatches = sync.matchCrosses();

        assertThat(crossedMatches).hasSize(10);
        assertThat(crossedMatches).extracting(Match::externalId, Match::whoscoredId)
                .contains(tuple("560600", "1983600"));
    }

    @Test
    void cruzaLosPartidosConUnaUnicaFilaDeEseLocalYEseVisitanteTambienLosFuturos() {
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, crossedSeasonMatches());
        sync.registerFixtures(MAN_UTD_WS.whoscoredId(), fixturesOf(MAN_UTD_WS));

        List<Match> crossedMatches = sync.matchCrosses();

        assertThat(crossedMatches).allSatisfy(match -> assertThat(match.whoscoredId())
                .isEqualTo(whoscoredIdOf(match.homeTeam(), match.awayTeam())));
        assertThat(crossedMatches).extracting(Match::status).contains(MatchStatus.TIMED, MatchStatus.FINISHED);
    }

    @Test
    void cruzaLosEquiposYTomaLosGuardadosComoEstadoActual() {
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, seasonMatches());
        sync.registerSourceTeams(List.of(MAN_UTD_WS));
        sync.registerFixtures(MAN_UTD_WS.whoscoredId(), fixturesOf(MAN_UTD_WS));

        List<Team> crossedTeams = sync.teamCrosses();
        sync.confirmTeams(crossedTeams);

        assertThat(crossedTeams).containsExactly(crossed(MAN_UTD));
        assertThat(sync.seasonTeams()).contains(crossed(MAN_UTD));
        assertThat(sync.pendingMatches()).filteredOn(match -> match.homeTeam().externalId().equals("66"))
                .allSatisfy(match -> assertThat(match.homeTeam().isCrossed()).isTrue());
    }

    @Test
    void omiteConEquipoSinCruzarOSinFilaLosPendientesQueSiguenSinId() {
        List<Team> versions = List.of(crossed(MAN_UTD), crossed(EVERTON), BRENTFORD, TOTTENHAM, ARSENAL, LIVERPOOL);
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, seasonMatches(versions));

        sync.closeDiscovery();
        LeagueStatsResult result = sync.result(LeagueStatsStatus.PROCESSED);

        assertThat(result.skippedMatches()).hasSize(21);
        assertThat(result.skippedMatches())
                .extracting(SkippedStatsMatch::matchExternalId, SkippedStatsMatch::reason)
                .contains(tuple(EVERTON_MAN_UTD_ID, StatsSkipReason.NOT_IN_SOURCE),
                        tuple("560600", StatsSkipReason.UNCROSSED_TEAM));
    }

    @Test
    void omiteUnPartidoSinDatosEnWhoScored() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();

        Optional<MatchStatsLoad> load = sync.prepare(evertonManUtd(), Optional.empty(), catalog);

        assertThat(load).isEmpty();
        assertThat(sync.result(LeagueStatsStatus.PROCESSED).skippedMatches())
                .containsExactly(skipped(StatsSkipReason.NOT_FINISHED_IN_SOURCE));
    }

    @Test
    void omiteUnPartidoQueWhoScoredNoDaPorTerminado() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();
        ReportedMatchStats notFinished = new ReportedMatchStats(EVERTON_MAN_UTD_STATS.whoscoredId(), false, "31",
                "32", 0, List.of(), List.of(), List.of());

        Optional<MatchStatsLoad> load = sync.prepare(evertonManUtd(), Optional.of(notFinished), catalog);

        assertThat(load).isEmpty();
        assertThat(sync.result(LeagueStatsStatus.PROCESSED).skippedMatches())
                .containsExactly(skipped(StatsSkipReason.NOT_FINISHED_IN_SOURCE));
    }

    @Test
    void omiteUnPartidoCuyaPaginaTraeOtrosEquipos() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();
        ReportedMatchStats reversed = new ReportedMatchStats(EVERTON_MAN_UTD_STATS.whoscoredId(), true, "32", "31",
                96, List.of(), List.of(), List.of());

        Optional<MatchStatsLoad> load = sync.prepare(evertonManUtd(), Optional.of(reversed), catalog);

        assertThat(load).isEmpty();
        assertThat(sync.result(LeagueStatsStatus.PROCESSED).skippedMatches())
                .containsExactly(skipped(StatsSkipReason.NOT_IN_SOURCE));
    }

    @Test
    void omiteUnPartidoConUnValorImposibleDeLaFuente() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();

        sync.recordInvalidSource(evertonManUtd());

        assertThat(sync.result(LeagueStatsStatus.PROCESSED).skippedMatches())
                .containsExactly(skipped(StatsSkipReason.INVALID_IN_SOURCE));
    }

    @Test
    void omiteUnPartidoQueNoSePudoGuardar() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();

        sync.recordWriteFailure(evertonManUtd());

        assertThat(sync.result(LeagueStatsStatus.PROCESSED).skippedMatches())
                .containsExactly(skipped(StatsSkipReason.WRITE_FAILED));
    }

    @Test
    void prepareArmaLasEstadisticasDeLosQueEntraronYDejaALosNoCruzadosEnElInforme() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();
        ReportedMatchStats withUnknownPlayer = withAppearance(EVERTON_MAN_UTD_STATS, new ReportedAppearance("400000",
                "Kobbie Mainoo", 21, Side.AWAY, false, 80, null, MetricCounts.empty(), null, false));

        Optional<MatchStatsLoad> load = sync.prepare(evertonManUtd(), Optional.of(withUnknownPlayer), catalog);

        assertThat(load).hasValueSatisfying(prepared -> {
            assertThat(prepared.source()).isEqualTo(LoadSource.SCRAPING);
            assertThat(prepared.match().statsLoaded()).isTrue();
            assertThat(prepared.stats()).hasSize(11)
                    .extracting(stats -> stats.player().externalId())
                    .doesNotContain("3259");
            assertThat(prepared.stats()).extracting(PlayerMatchStats::match).containsOnly(prepared.match());
            assertThat(prepared.crossedPlayers()).hasSize(10);
        });
        assertThat(sync.result(LeagueStatsStatus.PROCESSED).uncrossedPlayers())
                .containsExactly(new UncrossedPlayer("400000", "Kobbie Mainoo", "Manchester United FC",
                        EVERTON_MAN_UTD_ID, CrossFailure.NOT_FOUND));
    }

    @Test
    void recordSnapshotCuentaLoCargadoYSacaEsosPartidosDeLosPendientes() {
        LeagueStatsSync sync = new LeagueStatsSync(LEAGUE, SEASON, seasonMatches());
        StatsSnapshot withUnknownTeam = new StatsSnapshot(
                Stream.concat(snapshot().teams().stream(), Stream.of(new SnapshotTeamEntry(LEAGUE, "9999", "99")))
                        .toList(),
                snapshot().matches(), snapshot().stats());
        SnapshotLoad load = new SnapshotLoad(withUnknownTeam, seasonMatches(), sync.seasonTeams(), catalog,
                new CatalogReferences(teams(), seasonMatches()));

        sync.recordSnapshot(load, load.loads());
        LeagueStatsResult result = sync.result(LeagueStatsStatus.PROCESSED);

        assertThat(result.matchesFromSnapshot()).isEqualTo(1);
        assertThat(result.skippedSnapshotEntries())
                .containsExactly(Map.entry(SnapshotSkipReason.TEAM_NOT_IN_CATALOG, 1));
        assertThat(sync.pendingMatches()).extracting(Match::externalId)
                .hasSize(20)
                .doesNotContain(EVERTON_MAN_UTD_ID);
    }

    @Test
    void elResultadoProcesadoOCortadoTraeTodoYElNoAlcanzadoSoloLoDelSnapshot() {
        LeagueStatsSync sync = syncWithEvertonManUtdCrossed();
        Match manUtdLiverpool = matchBetween(crossedSeasonMatches(), MAN_UTD, LIVERPOOL);
        sync.recordScraped(new MatchStatsLoad(evertonManUtd().markStatsLoaded(), List.of(), List.of(),
                LoadSource.SCRAPING));
        sync.recordWriteFailure(manUtdLiverpool);

        LeagueStatsResult processed = sync.result(LeagueStatsStatus.PROCESSED);
        LeagueStatsResult cut = sync.result(LeagueStatsStatus.CUT);
        LeagueStatsResult notReached = sync.result(LeagueStatsStatus.NOT_REACHED);

        assertThat(processed)
                .extracting(LeagueStatsResult::status, LeagueStatsResult::matchesScraped,
                        result -> result.skippedMatches().size())
                .containsExactly(LeagueStatsStatus.PROCESSED, 1, 1);
        assertThat(cut)
                .extracting(LeagueStatsResult::status, LeagueStatsResult::matchesScraped,
                        result -> result.skippedMatches().size())
                .containsExactly(LeagueStatsStatus.CUT, 1, 1);
        assertThat(notReached).isEqualTo(LeagueStatsResult.notReached(LEAGUE, 0, Map.of()));
        assertThat(sync.pendingMatches()).extracting(Match::externalId).doesNotContain(EVERTON_MAN_UTD_ID);
    }

    /**
     * El 560603 ya tiene estadísticas y el 560604 está postergado: ninguno es pendiente.
     */
    private static Match withSomeExceptions(Match match) {
        if (match.externalId().equals(EVERTON_MAN_UTD_ID)) {
            return match.markStatsLoaded();
        }
        if (match.externalId().equals("560604")) {
            return new Match(match.id(), match.externalId(), match.season(), match.utcDate(), match.matchday(),
                    MatchStatus.POSTPONED, match.homeTeam(), match.awayTeam(), null, null, null);
        }
        return match;
    }

    private static boolean involvesManUtd(Match match) {
        return match.homeTeam().externalId().equals(MAN_UTD.externalId())
                || match.awayTeam().externalId().equals(MAN_UTD.externalId());
    }

    private static LeagueStatsSync syncWithEvertonManUtdCrossed() {
        List<Match> matches = crossedSeasonMatches().stream()
                .map(match -> match.externalId().equals(EVERTON_MAN_UTD_ID) ? evertonManUtd() : match)
                .toList();
        return new LeagueStatsSync(LEAGUE, SEASON, matches);
    }

    private static SkippedStatsMatch skipped(StatsSkipReason reason) {
        Match match = evertonManUtd();
        return new SkippedStatsMatch(EVERTON_MAN_UTD_ID, "Everton FC", "Manchester United FC", match.utcDate(), reason);
    }

    private static ReportedMatchStats withAppearance(ReportedMatchStats reported, ReportedAppearance appearance) {
        return new ReportedMatchStats(reported.whoscoredId(), reported.finished(), reported.homeTeamWhoscoredId(),
                reported.awayTeamWhoscoredId(), reported.endMinute(),
                Stream.concat(reported.appearances().stream(), Stream.of(appearance)).toList(), reported.goals(),
                reported.cards());
    }
}
