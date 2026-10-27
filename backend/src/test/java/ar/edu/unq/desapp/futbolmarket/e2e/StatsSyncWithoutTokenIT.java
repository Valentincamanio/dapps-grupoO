package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_MAN_UTD_STATS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_END;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_START;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeamOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.whoscoredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La sincronización de WhoScored sin la credencial de Football-Data, que es como queda el perfil
 * test: trabaja sobre los partidos ya guardados y no la necesita (FR-040). Es una clase aparte
 * porque {@code StatsSyncControllerIT} necesita el token para los 409 de la HU3 (research D26).
 *
 * <p>Usa un administrador al azar y una H2 propia, como {@code SyncDisabledIT}. El catálogo es el
 * Everton - Manchester United de la fecha 1 de {@link StatsFixtures}, con los equipos y el partido
 * cruzados y los jugadores de los dos equipos.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsSyncWithoutTokenIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String STATS_SYNC_PATH = "/matches/stats/sync";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int CURRENT_MATCHDAY = 8;
    private static final int PLAYERS_ON_PITCH = 11;

    private static final Match EVERTON_MAN_UTD = matchBetween(seasonMatches(), EVERTON, MAN_UTD);

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:stats-without-token-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private WhoScoredAdapter whoScoredAdapter;

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

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        seedCatalog();
        given(whoScoredAdapter.fetchMatchStats(whoscoredIdOf(EVERTON, MAN_UTD)))
                .willReturn(Optional.of(EVERTON_MAN_UTD_STATS));
        adminToken = new AuthTestHelper(mvc)
                .login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void sinTokenDeFootballDataLaSincronizacionDeWhoScoredRespondeElInformeYGuardaLasEstadisticas() throws Exception {
        MvcTestResult result = mvc.post().uri(STATS_SYNC_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .exchange();
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("leagues").get(0).get("league").asString()).isEqualTo("PREMIER");
        assertThat(report.get("leagues").get(0).get("matchesScraped").asInt()).isEqualTo(1);
        assertThat(statsRepository.findAll()).hasSize(PLAYERS_ON_PITCH);
        assertThat(matchRepository.findAllByExternalIds(List.of(EVERTON_MAN_UTD.externalId()))).singleElement()
                .satisfies(match -> assertThat(match.statsLoaded()).isTrue());
    }

    private void seedCatalog() {
        Season season = seasonRepository.save(new Season(null, SEASON_ID, League.PREMIER, SEASON_START, SEASON_END,
                CURRENT_MATCHDAY));
        Map<String, Team> teams = teamRepository.saveAll(List.of(EVERTON, MAN_UTD).stream()
                        .map(team -> new Team(team.externalId(), team.name(), null, team.league())
                                .crossWith(sourceTeamOf(team).whoscoredId()))
                        .toList())
                .stream()
                .collect(Collectors.toMap(Team::externalId, Function.identity()));
        playerRepository.saveAll(StatsFixtures.players().stream()
                .filter(player -> teams.containsKey(player.team().externalId()))
                .map(player -> new Player(null, player.externalId(), player.name(), player.position(),
                        teams.get(player.team().externalId()), player.dateOfBirth(), player.nationality(),
                        player.active(), player.whoscoredId()))
                .toList());
        matchRepository.saveAll(List.of(new Match(null, EVERTON_MAN_UTD.externalId(), season,
                EVERTON_MAN_UTD.utcDate(), EVERTON_MAN_UTD.matchday(), EVERTON_MAN_UTD.status(),
                teams.get(EVERTON.externalId()), teams.get(MAN_UTD.externalId()), EVERTON_MAN_UTD.fullTime(),
                EVERTON_MAN_UTD.halfTime(), EVERTON_MAN_UTD.winner()).crossWith(whoscoredIdOf(EVERTON, MAN_UTD))));
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
