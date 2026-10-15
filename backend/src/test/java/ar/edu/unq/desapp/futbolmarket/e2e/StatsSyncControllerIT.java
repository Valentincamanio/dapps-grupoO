package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BARRY;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BETO;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD_WS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.CASEMIRO;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.DAVIES;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.END_MINUTE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_MAN_UTD_STATS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_WS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.FERNANDES;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.GREALISH;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAGUIRE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MARTINEZ;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD_APPEARANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_END;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_START;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TARKOWSKI;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TARKOWSKI_APPEARANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.WISSA;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ZIRKZEE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ageOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.row;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeamOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.statsEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teamEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.whoscoredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredPages;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync.StatsSnapshotLineMapper;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La sincronización de WhoScored de punta a punta. El adapter está mockeado: ningún test consulta
 * WhoScored ni Football-Data (research D26).
 *
 * <p>Usa un administrador y un token de Football-Data al azar, una H2 propia y su propio archivo de
 * snapshot, que se borra antes de cada test: sin snapshot, todo lo pendiente se scrapea. Los tests
 * de la HU2 lo escriben con {@link StatsSnapshotLineMapper}. El token hace falta para los 409 de la
 * HU3, que se prueban acá con el candado común.</p>
 *
 * <p>El catálogo de cada test es una parte de {@link StatsFixtures}: el Everton, el Manchester
 * United y el Brentford cruzados, con sus jugadores; el Everton - Manchester United de la fecha 1 y
 * el Everton - Brentford de la fecha 5, terminados y sin estadísticas; y el Manchester United -
 * Everton de la fecha 8, programado. Los tres partidos ya tienen su id de WhoScored, así que la
 * corrida no necesita descubrir nada y solo pide las páginas de los dos terminados.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class StatsSyncControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final String SOURCE_TOKEN = "fd_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SNAPSHOT_FILE = "build/test-snapshot/stats-e2e-" + SUFFIX + ".jsonl";
    private static final Path SNAPSHOT_PATH = Path.of(SNAPSHOT_FILE);

    private static final String STATS_SYNC_PATH = "/matches/stats/sync";
    private static final String PLAYERS_SYNC_PATH = "/players/sync";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";

    private static final int CURRENT_MATCHDAY = 8;
    private static final int EVERTON_BRENTFORD_END_MINUTE = 94;
    private static final int BARRY_MINUTES = 24;

    private static final Match EVERTON_MAN_UTD = matchBetween(seasonMatches(), EVERTON, MAN_UTD);
    private static final Match EVERTON_BRENTFORD = matchBetween(seasonMatches(), EVERTON, BRENTFORD);
    private static final Match MAN_UTD_EVERTON = matchBetween(seasonMatches(), MAN_UTD, EVERTON);
    private static final Match TOTTENHAM_EVERTON = matchBetween(seasonMatches(), TOTTENHAM, EVERTON);
    private static final String EVERTON_MAN_UTD_WS_ID = whoscoredIdOf(EVERTON, MAN_UTD);
    private static final String EVERTON_BRENTFORD_WS_ID = whoscoredIdOf(EVERTON, BRENTFORD);

    /**
     * Los que entraron a la cancha en el Everton - Manchester United. Bayındır no entró.
     */
    private static final List<Player> EVERTON_MAN_UTD_ON_PITCH = List.of(PICKFORD, TARKOWSKI, NDIAYE, GREALISH, BETO,
            BARRY, CASEMIRO, MARTINEZ, MAGUIRE, FERNANDES, ZIRKZEE);

    private static final String NDIAYE_APPEARANCE_ID = StatsFixtures.NDIAYE_APPEARANCE.whoscoredId();

    private static final ReportedAppearance WISSA_APPEARANCE = new ReportedAppearance("380123", WISSA.name(),
            ageOf(WISSA), Side.AWAY, true, null, null, MetricCounts.of(Map.of(Metric.SHOTS, 3, Metric.PASSES, 18)),
            new BigDecimal("6.90"), false);
    private static final ReportedAppearance UNKNOWN_APPEARANCE = new ReportedAppearance("401999",
            "Harrison Armstrong", 19, Side.HOME, false, 80, null, MetricCounts.empty(), null, false);

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("futbolmarket.football-data.token", () -> SOURCE_TOKEN);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:stats-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
        registry.add("futbolmarket.whoscored.snapshot.path", () -> SNAPSHOT_FILE);
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private WhoScoredAdapter whoScoredAdapter;

    @MockitoBean
    private FootballDataAdapter footballDataAdapter;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerRepository playerRepository;

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

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final StatsSnapshotLineMapper lineMapper = new StatsSnapshotLineMapper();

    private AuthTestHelper helper;
    private String adminToken;
    private Season premierSeason;
    private Map<String, Team> savedTeams;

    /**
     * Los usuarios quedan entre tests: cada uno se registra con datos únicos.
     */
    @BeforeEach
    void setUp() throws Exception {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        lastFullSyncDAO.deleteAll();
        Files.deleteIfExists(SNAPSHOT_PATH);
        seedCatalog();
        given(whoScoredAdapter.fetchMatchStats(EVERTON_MAN_UTD_WS_ID)).willReturn(Optional.of(EVERTON_MAN_UTD_STATS));
        given(whoScoredAdapter.fetchMatchStats(EVERTON_BRENTFORD_WS_ID))
                .willReturn(Optional.of(evertonBrentfordStats()));
        helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void unaCompletaRespondeElInformeConLasCincoLigasProcesadasEnOrden() throws Exception {
        MvcTestResult result = synchronizeAs(adminToken);
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("type").asString()).isEqualTo("FULL");
        assertThat(report.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(report.get("snapshot").asString()).isEqualTo("MISSING");
        assertThat(report.get("cut").isNull()).isTrue();
        assertThat(report.get("leagues").values())
                .extracting(league -> league.get("league").asString(), league -> league.get("status").asString())
                .containsExactly(
                        tuple("PREMIER", "PROCESSED"),
                        tuple("BUNDESLIGA", "PROCESSED"),
                        tuple("LA_LIGA", "PROCESSED"),
                        tuple("SERIE_A", "PROCESSED"),
                        tuple("LIGUE_1", "PROCESSED"));
        JsonNode premier = report.get("leagues").get(0);
        assertThat(premier.get("matchesFromSnapshot").asInt()).isZero();
        assertThat(premier.get("matchesScraped").asInt()).isEqualTo(2);
        assertThat(premier.get("skippedMatches").values()).isEmpty();
        assertThat(premier.get("uncrossedTeams").values()).isEmpty();
        assertThat(premier.get("uncrossedPlayers").values()).isEmpty();
        assertThat(premier.get("skippedSnapshotEntries").values()).isEmpty();
    }

    @Test
    void cadaJugadorQueEntroQuedaConSusMetricasYLosDosTerminadosConLaMarca() throws Exception {
        synchronizeAs(adminToken);
        List<PlayerMatchStats> stats = statsRepository.findAll();

        assertThat(statsOf(stats, EVERTON_MAN_UTD)).extracting(row -> row.player().externalId())
                .containsExactlyInAnyOrderElementsOf(externalIdsOf(EVERTON_MAN_UTD_ON_PITCH));
        assertThat(statsOf(stats, EVERTON_BRENTFORD)).extracting(row -> row.player().externalId())
                .containsExactlyInAnyOrder(PICKFORD.externalId(), TARKOWSKI.externalId(), WISSA.externalId());
        assertThat(rowOf(stats, EVERTON_MAN_UTD, NDIAYE)).satisfies(ndiaye -> {
            assertThat(ndiaye.starter()).isTrue();
            assertThat(ndiaye.minutesPlayed()).isEqualTo(StatsFixtures.END_MINUTE);
            assertThat(ndiaye.counts().get(Metric.GOALS)).isEqualTo(1);
            assertThat(ndiaye.counts().get(Metric.SHOTS)).isEqualTo(4);
            assertThat(ndiaye.counts().get(Metric.PASSES)).isEqualTo(30);
            assertThat(ndiaye.rating()).isEqualByComparingTo("7.65");
            assertThat(ndiaye.manOfTheMatch()).isTrue();
        });
        assertThat(rowOf(stats, EVERTON_MAN_UTD, GREALISH).counts().get(Metric.ASSISTS)).isEqualTo(1);
        assertThat(rowOf(stats, EVERTON_MAN_UTD, BARRY).minutesPlayed()).isEqualTo(BARRY_MINUTES);
        assertThat(playerRepository.findByExternalId(NDIAYE.externalId()))
                .hasValueSatisfying(player -> assertThat(player.whoscoredId()).isEqualTo("393485"));
        assertThat(matchRepository.findAllByExternalIds(
                List.of(EVERTON_MAN_UTD.externalId(), EVERTON_BRENTFORD.externalId())))
                .hasSize(2)
                .allSatisfy(match -> assertThat(match.statsLoaded()).isTrue());
    }

    @Test
    void elPartidoProgramadoNoSeTocaNiSePideSuPagina() throws Exception {
        synchronizeAs(adminToken);

        assertThat(matchRepository.findAllByExternalIds(List.of(MAN_UTD_EVERTON.externalId()))).singleElement()
                .satisfies(match -> {
                    assertThat(match.status()).isEqualTo(MatchStatus.TIMED);
                    assertThat(match.whoscoredId()).isEqualTo(whoscoredIdOf(MAN_UTD, EVERTON));
                    assertThat(match.statsLoaded()).isFalse();
                });
        assertThat(statsOf(statsRepository.findAll(), MAN_UTD_EVERTON)).isEmpty();
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_MAN_UTD_WS_ID);
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_BRENTFORD_WS_ID);
        verifyNoMoreInteractions(whoScoredAdapter);
    }

    @Test
    void unJugadorQueNoEstaEnElCatalogoFiguraEntreLosSinCruzarYElPartidoSeGuardaConLosDemas() throws Exception {
        given(whoScoredAdapter.fetchMatchStats(EVERTON_MAN_UTD_WS_ID))
                .willReturn(Optional.of(withAppearance(EVERTON_MAN_UTD_STATS, UNKNOWN_APPEARANCE)));

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);
        List<PlayerMatchStats> stats = statsRepository.findAll();

        assertThat(premier.get("uncrossedPlayers").values()).singleElement().satisfies(uncrossed -> {
            assertThat(uncrossed.get("whoscoredId").asString()).isEqualTo("401999");
            assertThat(uncrossed.get("name").asString()).isEqualTo("Harrison Armstrong");
            assertThat(uncrossed.get("team").asString()).isEqualTo(EVERTON.name());
            assertThat(uncrossed.get("match").asString()).isEqualTo(EVERTON_MAN_UTD.externalId());
            assertThat(uncrossed.get("reason").asString()).isEqualTo("NOT_FOUND");
        });
        assertThat(premier.get("matchesScraped").asInt()).isEqualTo(2);
        assertThat(statsOf(stats, EVERTON_MAN_UTD)).hasSize(EVERTON_MAN_UTD_ON_PITCH.size());
    }

    @Test
    void unEquipoSinCruzarOmiteSusPartidosYLosDemasSeProcesan() throws Exception {
        Team tottenham = teamRepository.save(new Team(TOTTENHAM.externalId(), TOTTENHAM.name(), null, League.PREMIER));
        matchRepository.saveAll(
                List.of(seasonMatch(TOTTENHAM_EVERTON, tottenham, savedTeams.get(EVERTON.externalId()))));
        given(whoScoredAdapter.fetchLeagueTeams(League.PREMIER)).willReturn(sourceTeams());

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);

        assertThat(premier.get("status").asString()).isEqualTo("PROCESSED");
        assertThat(premier.get("skippedMatches").values()).singleElement().satisfies(skipped -> {
            assertThat(skipped.get("externalId").asString()).isEqualTo(TOTTENHAM_EVERTON.externalId());
            assertThat(skipped.get("homeTeam").asString()).isEqualTo(TOTTENHAM.name());
            assertThat(skipped.get("awayTeam").asString()).isEqualTo(EVERTON.name());
            assertThat(Instant.parse(skipped.get("utcDate").asString())).isEqualTo(TOTTENHAM_EVERTON.utcDate());
            assertThat(skipped.get("reason").asString()).isEqualTo("UNCROSSED_TEAM");
        });
        assertThat(premier.get("uncrossedTeams").values()).singleElement().satisfies(uncrossed -> {
            assertThat(uncrossed.get("externalId").asString()).isEqualTo(TOTTENHAM.externalId());
            assertThat(uncrossed.get("name").asString()).isEqualTo(TOTTENHAM.name());
            assertThat(uncrossed.get("reason").asString()).isEqualTo("NOT_FOUND");
        });
        assertThat(premier.get("matchesScraped").asInt()).isEqualTo(2);
        assertThat(matchRepository.findAllByExternalIds(List.of(TOTTENHAM_EVERTON.externalId()))).singleElement()
                .satisfies(match -> assertThat(match.statsLoaded()).isFalse());
    }

    @Test
    void unaSegundaCompletaNoPideNingunaPaginaDePartidoYLasFilasQuedanIguales() throws Exception {
        synchronizeAs(adminToken);
        List<PlayerMatchStats> before = statsRepository.findAll();
        clearInvocations(whoScoredAdapter);

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);
        List<PlayerMatchStats> after = statsRepository.findAll();

        assertThat(premier.get("matchesScraped").asInt()).isZero();
        assertThat(premier.get("skippedMatches").values()).isEmpty();
        verifyNoInteractions(whoScoredAdapter);
        assertThat(after)
                .extracting(PlayerMatchStats::id, row -> row.player().externalId(), row -> row.match().externalId(),
                        PlayerMatchStats::minutesPlayed, PlayerMatchStats::counts, PlayerMatchStats::rating)
                .containsExactlyInAnyOrderElementsOf(before.stream()
                        .map(row -> tuple(row.id(), row.player().externalId(), row.match().externalId(),
                                row.minutesPlayed(), row.counts(), row.rating()))
                        .toList());
    }

    @Test
    void unaSolaLigaRespondeSingleLeagueYSoloPideLasPaginasDeEsaLiga() throws Exception {
        Match bundesligaMatch = seedBundesligaMatch();

        MvcTestResult result = synchronizeLeagueAs(adminToken, "PREMIER");
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("type").asString()).isEqualTo("SINGLE_LEAGUE");
        assertThat(report.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(report.get("leagues").values())
                .extracting(league -> league.get("league").asString())
                .containsExactly("PREMIER");
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_MAN_UTD_WS_ID);
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_BRENTFORD_WS_ID);
        verifyNoMoreInteractions(whoScoredAdapter);
        assertThat(matchRepository.findAllByExternalIds(List.of(bundesligaMatch.externalId()))).singleElement()
                .satisfies(match -> assertThat(match.statsLoaded()).isFalse());
    }

    @Test
    void unaLigaQueNoEsUnaDeLasCincoResponde400ConElValorRecibidoSinConsultarWhoScored() throws Exception {
        MvcTestResult result = synchronizeLeagueAs(adminToken, "MLS");
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("message").asString())
                .isEqualTo("La liga 'MLS' no es una de las admitidas: PREMIER, BUNDESLIGA, LA_LIGA, SERIE_A, LIGUE_1.");
        assertThat(body.get("path").asString()).isEqualTo(STATS_SYNC_PATH);
        verifyNoInteractions(whoScoredAdapter);
    }

    @Test
    void unUsuarioComunRecibe403SinQueSeConsulteWhoScored() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri(STATS_SYNC_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        verifyNoInteractions(whoScoredAdapter);
    }

    @Test
    void sinCredencialResponde401SinQueSeConsulteWhoScored() throws Exception {
        MvcTestResult result = mvc.post().uri(STATS_SYNC_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        verifyNoInteractions(whoScoredAdapter);
    }

    @Test
    void laSincronizacionDeWhoScoredNoConsultaFootballData() throws Exception {
        MvcTestResult result = synchronizeAs(adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        verifyNoInteractions(footballDataAdapter);
    }

    @Test
    void laSincronizacionDeFootballDataNoConsultaWhoScored() throws Exception {
        willThrow(new ExternalSourceException(TIMEOUT_REASON)).given(footballDataAdapter).fetchLeague(any());

        MvcTestResult result = mvc.post().uri(PLAYERS_SYNC_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        verifyNoInteractions(whoScoredAdapter);
    }

    /**
     * El snapshot de {@link StatsFixtures#snapshot()} trae las estadísticas del Everton - Manchester
     * United, de Pickford y Casemiro.
     */
    @Test
    void unSnapshotConUnoDeLosTerminadosLoCargaSinPedirSuPaginaYElOtroSeScrapea() throws Exception {
        writeSnapshot(StatsFixtures.snapshot());

        JsonNode report = body(synchronizeAs(adminToken));
        List<PlayerMatchStats> stats = statsRepository.findAll();

        JsonNode premier = report.get("leagues").get(0);
        assertThat(report.get("snapshot").asString()).isEqualTo("LOADED");
        assertThat(premier.get("matchesFromSnapshot").asInt()).isEqualTo(1);
        assertThat(premier.get("matchesScraped").asInt()).isEqualTo(1);
        assertThat(statsOf(stats, EVERTON_MAN_UTD))
                .extracting(row -> row.player().externalId(), PlayerMatchStats::minutesPlayed)
                .containsExactlyInAnyOrder(tuple(PICKFORD.externalId(), END_MINUTE), tuple(CASEMIRO.externalId(), 62));
        assertThat(rowOf(stats, EVERTON_MAN_UTD, PICKFORD).rating()).isEqualByComparingTo("6.80");
        assertThat(statsOf(stats, EVERTON_BRENTFORD)).hasSize(3);
        assertThat(matchRepository.findAllByExternalIds(
                List.of(EVERTON_MAN_UTD.externalId(), EVERTON_BRENTFORD.externalId())))
                .allSatisfy(match -> assertThat(match.statsLoaded()).isTrue());
        verify(whoScoredAdapter, never()).fetchMatchStats(EVERTON_MAN_UTD_WS_ID);
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_BRENTFORD_WS_ID);
    }

    /**
     * El Tottenham y el Tottenham - Everton de la fecha 3 están en el catálogo sin cruzar, y Ndiaye
     * también. El snapshot trae los tres ids.
     */
    @Test
    void losCrucesDelSnapshotQuedanGuardados() throws Exception {
        Team tottenham = teamRepository.save(new Team(TOTTENHAM.externalId(), TOTTENHAM.name(), null, League.PREMIER));
        matchRepository.saveAll(
                List.of(seasonMatch(TOTTENHAM_EVERTON, tottenham, savedTeams.get(EVERTON.externalId()))));
        writeSnapshot(new StatsSnapshot(
                List.of(teamEntry(TOTTENHAM)),
                List.of(matchEntry(TOTTENHAM, EVERTON)),
                List.of(statsEntry(TOTTENHAM, EVERTON, row(NDIAYE, NDIAYE_APPEARANCE_ID, END_MINUTE)))));

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);

        assertThat(premier.get("matchesFromSnapshot").asInt()).isEqualTo(1);
        assertThat(premier.get("uncrossedTeams").values()).isEmpty();
        assertThat(teamRepository.findAllByExternalIds(List.of(TOTTENHAM.externalId()))).singleElement()
                .satisfies(team -> assertThat(team.whoscoredId()).isEqualTo(sourceTeamOf(TOTTENHAM).whoscoredId()));
        assertThat(matchRepository.findAllByExternalIds(List.of(TOTTENHAM_EVERTON.externalId()))).singleElement()
                .satisfies(match -> {
                    assertThat(match.whoscoredId()).isEqualTo(whoscoredIdOf(TOTTENHAM, EVERTON));
                    assertThat(match.statsLoaded()).isTrue();
                });
        assertThat(playerRepository.findByExternalId(NDIAYE.externalId()))
                .hasValueSatisfying(player -> assertThat(player.whoscoredId()).isEqualTo(NDIAYE_APPEARANCE_ID));
    }

    @Test
    void conUnBloqueoDesdeElPrimerPedidoLoDelSnapshotSeCargaIgualYElCorteLlegaDespues() throws Exception {
        writeSnapshot(StatsFixtures.snapshot());
        willThrow(new SourceBlockedException(WhoScoredPages.match(EVERTON_BRENTFORD_WS_ID)))
                .given(whoScoredAdapter).fetchMatchStats(any());

        MvcTestResult result = synchronizeAs(adminToken);
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("snapshot").asString()).isEqualTo("LOADED");
        JsonNode cut = report.get("cut");
        assertThat(cut.get("page").asString()).isEqualTo("MATCH");
        assertThat(cut.get("whoscoredId").asString()).isEqualTo(EVERTON_BRENTFORD_WS_ID);
        assertThat(cut.get("path").asString()).isEqualTo("/matches/" + EVERTON_BRENTFORD_WS_ID + "/live");
        assertThat(cut.get("league").asString()).isEqualTo("PREMIER");
        assertThat(cut.get("match").asString()).isEqualTo(EVERTON_BRENTFORD.externalId());
        assertThat(report.get("leagues").values())
                .extracting(league -> league.get("league").asString(), league -> league.get("status").asString(),
                        league -> league.get("matchesFromSnapshot").asInt())
                .containsExactly(
                        tuple("PREMIER", "CUT", 1),
                        tuple("BUNDESLIGA", "NOT_REACHED", 0),
                        tuple("LA_LIGA", "NOT_REACHED", 0),
                        tuple("SERIE_A", "NOT_REACHED", 0),
                        tuple("LIGUE_1", "NOT_REACHED", 0));
        assertThat(matchRepository.findAllByExternalIds(List.of(EVERTON_MAN_UTD.externalId()))).singleElement()
                .satisfies(match -> assertThat(match.statsLoaded()).isTrue());
        assertThat(matchRepository.findAllByExternalIds(List.of(EVERTON_BRENTFORD.externalId()))).singleElement()
                .satisfies(match -> assertThat(match.statsLoaded()).isFalse());
    }

    /**
     * El Arsenal - Liverpool no está en el catálogo, y Davies, del Tottenham, tampoco.
     */
    @Test
    void lasEntradasQueNoEstanEnElCatalogoSeSalteanSeCuentanYElRestoSeCarga() throws Exception {
        writeSnapshot(new StatsSnapshot(
                List.of(teamEntry(EVERTON), teamEntry(MAN_UTD)),
                List.of(matchEntry(EVERTON, MAN_UTD), matchEntry(ARSENAL, LIVERPOOL)),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "93145", END_MINUTE), row(CASEMIRO, "75577", 62),
                        row(DAVIES, "90001", END_MINUTE)))));

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);
        List<PlayerMatchStats> stats = statsRepository.findAll();

        assertThat(premier.get("skippedSnapshotEntries").values())
                .extracting(entry -> entry.get("reason").asString(), entry -> entry.get("count").asInt())
                .containsExactly(tuple("MATCH_NOT_IN_CATALOG", 1), tuple("PLAYER_NOT_IN_CATALOG", 1));
        assertThat(premier.get("matchesFromSnapshot").asInt()).isEqualTo(1);
        assertThat(statsOf(stats, EVERTON_MAN_UTD)).extracting(row -> row.player().externalId())
                .containsExactlyInAnyOrder(PICKFORD.externalId(), CASEMIRO.externalId());
    }

    @Test
    void unSnapshotConUnaLineaRotaSeIgnoraConUnWarnYSeScrapeaTodo(CapturedOutput output) throws Exception {
        List<String> lines = new ArrayList<>(lineMapper.toLines(StatsFixtures.snapshot()));
        lines.set(1, "{\"type\":\"team\",\"league\":\"PREMIER\",");
        writeSnapshotLines(lines);

        JsonNode report = body(synchronizeAs(adminToken));

        JsonNode premier = report.get("leagues").get(0);
        assertThat(report.get("snapshot").asString()).isEqualTo("UNREADABLE");
        assertThat(premier.get("matchesFromSnapshot").asInt()).isZero();
        assertThat(premier.get("matchesScraped").asInt()).isEqualTo(2);
        assertThat(output.getAll().lines().filter(line -> line.contains("No se pudo leer el snapshot")).findFirst())
                .hasValueSatisfying(line -> assertThat(line).contains("WARN", SNAPSHOT_FILE));
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_MAN_UTD_WS_ID);
        verify(whoScoredAdapter).fetchMatchStats(EVERTON_BRENTFORD_WS_ID);
    }

    @Test
    void unPartidoQueYaTeniaEstadisticasConservaLasSuyas() throws Exception {
        synchronizeAs(adminToken);
        List<PlayerMatchStats> before = statsOf(statsRepository.findAll(), EVERTON_MAN_UTD);
        writeSnapshot(StatsFixtures.snapshot());

        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);
        List<PlayerMatchStats> after = statsOf(statsRepository.findAll(), EVERTON_MAN_UTD);

        assertThat(premier.get("matchesFromSnapshot").asInt()).isZero();
        assertThat(premier.get("skippedSnapshotEntries").values()).isEmpty();
        assertThat(after).hasSize(EVERTON_MAN_UTD_ON_PITCH.size())
                .extracting(PlayerMatchStats::id, row -> row.player().externalId(), PlayerMatchStats::minutesPlayed,
                        PlayerMatchStats::counts, PlayerMatchStats::rating)
                .containsExactlyInAnyOrderElementsOf(before.stream()
                        .map(row -> tuple(row.id(), row.player().externalId(), row.minutesPlayed(), row.counts(),
                                row.rating()))
                        .toList());
    }

    /**
     * La temporada de la Premier, el Everton, el Manchester United y el Brentford cruzados con sus
     * jugadores, y los tres partidos con su id de WhoScored.
     */
    private void seedCatalog() {
        premierSeason = seasonRepository.save(new Season(null, SEASON_ID, League.PREMIER, SEASON_START, SEASON_END,
                CURRENT_MATCHDAY));
        List<Team> teams = teamRepository.saveAll(List.of(EVERTON, MAN_UTD, BRENTFORD).stream()
                .map(team -> new Team(team.externalId(), team.name(), null, team.league())
                        .crossWith(sourceTeamOf(team).whoscoredId()))
                .toList());
        savedTeams = new HashMap<>();
        teams.forEach(team -> savedTeams.put(team.externalId(), team));
        playerRepository.saveAll(StatsFixtures.players().stream()
                .filter(player -> savedTeams.containsKey(player.team().externalId()))
                .map(player -> new Player(null, player.externalId(), player.name(), player.position(),
                        savedTeams.get(player.team().externalId()), player.dateOfBirth(), player.nationality(),
                        player.active(), player.whoscoredId()))
                .toList());
        matchRepository.saveAll(List.of(
                crossedSeasonMatch(EVERTON_MAN_UTD, EVERTON, MAN_UTD),
                crossedSeasonMatch(EVERTON_BRENTFORD, EVERTON, BRENTFORD),
                crossedSeasonMatch(MAN_UTD_EVERTON, MAN_UTD, EVERTON)));
    }

    private Match crossedSeasonMatch(Match template, Team home, Team away) {
        return seasonMatch(template, savedTeams.get(home.externalId()), savedTeams.get(away.externalId()))
                .crossWith(whoscoredIdOf(home, away));
    }

    /**
     * El partido de {@link StatsFixtures} con la temporada y los equipos guardados.
     */
    private Match seasonMatch(Match template, Team home, Team away) {
        return new Match(null, template.externalId(), premierSeason, template.utcDate(), template.matchday(),
                template.status(), home, away, template.fullTime(), template.halfTime(), template.winner());
    }

    /**
     * Un partido de la Bundesliga terminado, cruzado y sin estadísticas, con su temporada y sus
     * equipos.
     */
    private Match seedBundesligaMatch() {
        Season season = seasonRepository.save(new Season(null, "2601", League.BUNDESLIGA, SEASON_START, SEASON_END,
                CURRENT_MATCHDAY));
        List<Team> teams = teamRepository.saveAll(List.of(
                new Team("5", "FC Bayern München", null, League.BUNDESLIGA).crossWith("37"),
                new Team("4", "Borussia Dortmund", null, League.BUNDESLIGA).crossWith("44")));
        return matchRepository.saveAll(List.of(new Match(null, "540001", season,
                Instant.parse("2026-08-28T18:30:00Z"), 1, MatchStatus.FINISHED, teams.getFirst(), teams.getLast(),
                new Score(2, 1), new Score(1, 0), MatchWinner.HOME_TEAM).crossWith("1990001"))).getFirst();
    }

    /**
     * El Everton - Brentford de la fecha 5: Pickford y Tarkowski de local y Wissa, que en el catálogo
     * es del Brentford, de visitante.
     */
    private static ReportedMatchStats evertonBrentfordStats() {
        return new ReportedMatchStats(EVERTON_BRENTFORD_WS_ID, true, EVERTON_WS.whoscoredId(),
                BRENTFORD_WS.whoscoredId(), EVERTON_BRENTFORD_END_MINUTE,
                List.of(PICKFORD_APPEARANCE, TARKOWSKI_APPEARANCE, WISSA_APPEARANCE), List.of(), List.of());
    }

    private static ReportedMatchStats withAppearance(ReportedMatchStats stats, ReportedAppearance appearance) {
        List<ReportedAppearance> appearances = new ArrayList<>(stats.appearances());
        appearances.add(appearance);
        return new ReportedMatchStats(stats.whoscoredId(), stats.finished(), stats.homeTeamWhoscoredId(),
                stats.awayTeamWhoscoredId(), stats.endMinute(), appearances, stats.goals(), stats.cards());
    }

    private void writeSnapshot(StatsSnapshot snapshot) throws IOException {
        writeSnapshotLines(lineMapper.toLines(snapshot));
    }

    /**
     * Escribe las líneas como el repository: UTF-8 y LF, también al final.
     */
    private static void writeSnapshotLines(List<String> lines) throws IOException {
        Files.createDirectories(SNAPSHOT_PATH.getParent());
        Files.writeString(SNAPSHOT_PATH, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static List<String> externalIdsOf(List<Player> players) {
        return players.stream().map(Player::externalId).toList();
    }

    private static List<PlayerMatchStats> statsOf(List<PlayerMatchStats> stats, Match match) {
        return stats.stream().filter(row -> row.match().externalId().equals(match.externalId())).toList();
    }

    private static PlayerMatchStats rowOf(List<PlayerMatchStats> stats, Match match, Player player) {
        return statsOf(stats, match).stream()
                .filter(row -> row.player().externalId().equals(player.externalId()))
                .findFirst()
                .orElseThrow();
    }

    private MvcTestResult synchronizeAs(String sessionToken) {
        return mvc.post().uri(STATS_SYNC_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + sessionToken).exchange();
    }

    private MvcTestResult synchronizeLeagueAs(String sessionToken, String league) {
        return mvc.post().uri(STATS_SYNC_PATH)
                .param("league", league)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + sessionToken)
                .exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
