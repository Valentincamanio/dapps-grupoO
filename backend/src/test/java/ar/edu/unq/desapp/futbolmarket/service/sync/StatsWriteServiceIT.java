package ar.edu.unq.desapp.futbolmarket.service.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LoadSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchStatsLoad;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.ALISSON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.finishedMatchId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class StatsWriteServiceIT {
    private static final MetricCounts COUNTS = MetricCounts.of(Map.of(Metric.PASSES, 30, Metric.TACKLES, 2));

    @Autowired
    private StatsWriteService statsWriteService;

    @Autowired
    private SyncWriteService syncWriteService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerMatchStatsRepository statsRepository;

    @Autowired
    private PlayerMatchStatsSQLDAO statsDAO;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @Autowired
    private LastFullSyncSQLDAO lastFullSyncDAO;

    @BeforeEach
    void cleanDatabaseBefore() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void saveMatchStatsGuardaLasFilasLosIdsDeLosJugadoresYLaMarcaDelPartido() {
        Catalog catalog = evertonManUtdCatalog();
        Match loaded = catalog.match().markStatsLoaded();
        Player ndiaye = catalog.ndiaye().crossWith("393485");
        MatchStatsLoad load = new MatchStatsLoad(loaded, List.of(ndiaye),
                List.of(stats(catalog.pickford(), loaded), stats(ndiaye, loaded)), LoadSource.SCRAPING);

        statsWriteService.saveMatchStats(load);

        assertThat(statsRepository.findAll()).extracting(stats -> stats.player().externalId())
                .containsExactlyInAnyOrder("3204", "8135");
        assertThat(matchRepository.findAllByExternalIds(List.of("560603")))
                .singleElement().satisfies(match -> assertThat(match.statsLoaded()).isTrue());
        assertThat(playerRepository.findByExternalId("8135"))
                .hasValueSatisfying(player -> assertThat(player.whoscoredId()).isEqualTo("393485"));
    }

    @Test
    void unaCargaConDosFilasDelMismoJugadorNoDejaNada() {
        Catalog catalog = evertonManUtdCatalog();
        Match loaded = catalog.match().markStatsLoaded();
        Player ndiaye = catalog.ndiaye().crossWith("393485");
        MatchStatsLoad load = new MatchStatsLoad(loaded, List.of(ndiaye),
                List.of(stats(catalog.pickford(), loaded), stats(catalog.pickford(), loaded)), LoadSource.SCRAPING);

        assertThatThrownBy(() -> statsWriteService.saveMatchStats(load))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(statsDAO.count()).isZero();
        assertThat(matchRepository.findAllByExternalIds(List.of("560603")))
                .singleElement().satisfies(match -> assertThat(match.statsLoaded()).isFalse());
        assertThat(playerRepository.findByExternalId("8135"))
                .hasValueSatisfying(player -> assertThat(player.whoscoredId()).isNull());
    }

    @Test
    void guardaLosCrucesDeEquiposYDePartidos() {
        Catalog catalog = evertonManUtdCatalog();

        statsWriteService.saveTeamCrosses(List.of(catalog.everton().crossWith("31")));
        statsWriteService.saveMatchCrosses(List.of(catalog.match().crossWith("1983603")));

        assertThat(teamRepository.findAllCrossed()).extracting(Team::externalId, Team::whoscoredId)
                .contains(tuple("62", "31"));
        assertThat(matchRepository.findAllCrossed()).extracting(Match::whoscoredId).contains("1983603");
    }

    /**
     * El catálogo lo arma la sincronización de la 005, como en {@code SyncWriteServiceIT}. Volver a
     * aplicar la misma liga tiene que conservar todo lo que agregó WhoScored (SC-010).
     */
    @Test
    void unaSincronizacionDeFootballDataPosteriorConservaLosCrucesYLaMarca() {
        LeagueSnapshot premier = snapshot(League.PREMIER);
        SquadAssignment assignment = SquadAssignment.of(List.of(premier)).resolve(List.of());
        syncWriteService.applyLeague(premier, assignment);
        Team liverpool = statsWriteService.saveTeamCrosses(
                List.of(teamRepository.findAllByExternalIds(List.of(LIVERPOOL_ID)).getFirst().crossWith("26")))
                .getFirst();
        Match finished = statsWriteService.saveMatchCrosses(List.of(matchRepository
                .findAllByExternalIds(List.of(finishedMatchId(League.PREMIER))).getFirst().crossWith("1983700")))
                .getFirst().markStatsLoaded();
        Player alisson = playerRepository.findByExternalId(ALISSON_ID).orElseThrow().crossWith("93000");
        statsWriteService.saveMatchStats(new MatchStatsLoad(finished, List.of(alisson),
                List.of(stats(alisson, finished)), LoadSource.SCRAPING));
        List<PlayerMatchStats> statsBefore = statsRepository.findAll();

        syncWriteService.applyLeague(premier, assignment);

        assertThat(teamRepository.findAllByExternalIds(List.of(LIVERPOOL_ID))).singleElement()
                .satisfies(team -> assertThat(team.whoscoredId()).isEqualTo(liverpool.whoscoredId()));
        assertThat(playerRepository.findByExternalId(ALISSON_ID))
                .hasValueSatisfying(player -> assertThat(player.whoscoredId()).isEqualTo("93000"));
        assertThat(matchRepository.findAllByExternalIds(List.of(finishedMatchId(League.PREMIER)))).singleElement()
                .satisfies(match -> {
                    assertThat(match.whoscoredId()).isEqualTo("1983700");
                    assertThat(match.statsLoaded()).isTrue();
                });
        assertThat(statsRepository.findAll())
                .extracting(PlayerMatchStats::id, PlayerMatchStats::minutesPlayed, PlayerMatchStats::counts,
                        PlayerMatchStats::rating)
                .containsExactlyElementsOf(statsBefore.stream()
                        .map(stats -> tuple(stats.id(), stats.minutesPlayed(),
                                stats.counts(), stats.rating()))
                        .toList());
    }

    /**
     * La temporada, el Everton y el Manchester United, el Everton - Manchester United de la fecha 1 y
     * dos jugadores del Everton: Pickford, ya cruzado, y Ndiaye, sin cruzar.
     */
    private Catalog evertonManUtdCatalog() {
        Season season = seasonRepository.save(new Season(null, StatsFixtures.SEASON_ID, League.PREMIER,
                StatsFixtures.SEASON_START, StatsFixtures.SEASON_END, 8));
        List<Team> teams = teamRepository.saveAll(List.of(
                new Team(StatsFixtures.EVERTON.externalId(), StatsFixtures.EVERTON.name(), null, League.PREMIER),
                new Team(StatsFixtures.MAN_UTD.externalId(), StatsFixtures.MAN_UTD.name(), null, League.PREMIER)));
        Team everton = teams.getFirst();
        Team manUtd = teams.getLast();
        Match template = StatsFixtures.evertonManUtd();
        Match match = matchRepository.saveAll(List.of(new Match(null, template.externalId(), season,
                template.utcDate(), template.matchday(), template.status(), everton, manUtd, template.fullTime(),
                template.halfTime(), template.winner()))).getFirst();
        List<Player> players = playerRepository.saveAll(List.of(
                new Player(null, "3204", "Jordan Pickford", Position.GOALKEEPER, everton, null, null, true, "93145"),
                new Player("8135", "Iliman Ndiaye", Position.FORWARD, everton)));
        return new Catalog(everton, match, players.getFirst(), players.getLast());
    }

    private static PlayerMatchStats stats(Player player, Match match) {
        return new PlayerMatchStats(null, player, match, true, 90, COUNTS, new BigDecimal("7.65"), false);
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        lastFullSyncDAO.deleteAll();
    }

    private record Catalog(Team everton, Match match, Player pickford, Player ndiaye) {
    }
}
