package ar.edu.unq.desapp.futbolmarket.e2e;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_CREST;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La sincronización manual de punta a punta (HU1). El adapter está mockeado y devuelve los
 * snapshots de {@code SnapshotFixtures}: ningún test llama a la API real (research D21).
 *
 * <p>Usa un administrador y un token de la fuente al azar y una H2 propia, igual que
 * {@code AdminAccountIT}. La captura de la salida queda declarada para los escenarios de la HU3,
 * que verifican que el token no aparezca en el registro.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SyncControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final String SOURCE_TOKEN = "fd_" + UUID.randomUUID().toString().replace("-", "");

    private static final String SYNC_PATH = "/players/sync";
    private static final String PLAYERS_PATH = "/players";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";

    private static final int TEAMS_PER_LEAGUE = 2;
    private static final int PLAYERS_PER_LEAGUE = 8;
    private static final int MATCHES_PER_LEAGUE = 2;
    private static final int LIVERPOOL_PLAYERS = 4;
    private static final int CHELSEA_SAVED_PLAYERS = 4;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("futbolmarket.football-data.token", () -> SOURCE_TOKEN);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:sync-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FootballDataAdapter adapter;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private String adminToken;

    /**
     * Los usuarios quedan entre tests: cada uno se registra con datos únicos.
     */
    @BeforeEach
    void setUp() throws Exception {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        given(adapter.fetchLeague(any())).willAnswer(invocation -> snapshot(invocation.<League>getArgument(0)));
        helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void unaCompletaRespondeElInformeConLasCincoLigasProcesadasEnOrdenYSusCreados() throws Exception {
        MvcTestResult result = synchronizeAs(adminToken);
        JsonNode report = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(report.get("type").asString()).isEqualTo("FULL");
        assertThat(report.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(report.get("inactivationApplied").asBoolean()).isTrue();
        assertThat(report.get("leagues").values())
                .extracting(league -> league.get("league").asString(), league -> league.get("status").asString())
                .containsExactly(
                        tuple("PREMIER", "SUCCEEDED"),
                        tuple("BUNDESLIGA", "SUCCEEDED"),
                        tuple("LA_LIGA", "SUCCEEDED"),
                        tuple("SERIE_A", "SUCCEEDED"),
                        tuple("LIGUE_1", "SUCCEEDED"));
        assertThat(report.get("leagues").values()).allSatisfy(league -> {
            assertThat(league.get("teams").get("created").asInt()).isEqualTo(TEAMS_PER_LEAGUE);
            assertThat(league.get("players").get("created").asInt()).isEqualTo(PLAYERS_PER_LEAGUE);
            assertThat(league.get("matches").get("created").asInt()).isEqualTo(MATCHES_PER_LEAGUE);
        });
    }

    @Test
    void despuesDeSincronizarElListadoYElDetalleMuestranLosDatosNuevosDelJugador() throws Exception {
        synchronizeAs(adminToken);
        JsonNode alissonInPage = playerNamed(playersOf(LIVERPOOL_NAME), "Alisson Becker");
        JsonNode alissonDetail = body(mvc.get().uri(PLAYERS_PATH + "/{id}", alissonInPage.get("id").asLong()).exchange());

        assertThat(List.of(alissonInPage, alissonDetail)).allSatisfy(alisson -> {
            assertThat(alisson.get("team").asString()).isEqualTo(LIVERPOOL_NAME);
            assertThat(alisson.get("league").asString()).isEqualTo("PREMIER");
            assertThat(alisson.get("position").asString()).isEqualTo("GOALKEEPER");
            assertThat(alisson.get("dateOfBirth").asString()).isEqualTo("1992-10-02");
            assertThat(alisson.get("nationality").asString()).isEqualTo("Brazil");
            assertThat(alisson.get("teamCrest").asString()).isEqualTo(LIVERPOOL_CREST);
            assertThat(alisson.get("active").asBoolean()).isTrue();
        });
    }

    @Test
    void unJugadorNuevoSinPosicionFiguraEntreLosOmitidosYNoEstaEnElCatalogo() throws Exception {
        JsonNode premier = body(synchronizeAs(adminToken)).get("leagues").get(0);
        JsonNode chelseaPage = playersOf(CHELSEA_NAME);

        assertThat(premier.get("players").get("skipped").asInt()).isEqualTo(1);
        assertThat(premier.get("skippedPlayers").values()).singleElement().satisfies(skipped -> {
            assertThat(skipped.get("externalId").asString()).isEqualTo(NO_POSITION_PLAYER_ID);
            assertThat(skipped.get("name").asString()).isEqualTo(NO_POSITION_PLAYER_NAME);
            assertThat(skipped.get("team").asString()).isEqualTo(CHELSEA_NAME);
            assertThat(skipped.get("reason").asString()).isEqualTo("MISSING_POSITION");
        });
        assertThat(chelseaPage.get("content").values())
                .hasSize(CHELSEA_SAVED_PLAYERS)
                .extracting(player -> player.get("name").asString())
                .doesNotContain(NO_POSITION_PLAYER_NAME);
    }

    @Test
    void elFiltroPorEquipoComparaContraElNombreOficialYNoContraElCorto() throws Exception {
        synchronizeAs(adminToken);

        JsonNode officialName = playersOf(LIVERPOOL_NAME);
        JsonNode shortName = playersOf("Liverpool");

        assertThat(officialName.get("totalElements").asLong()).isEqualTo(LIVERPOOL_PLAYERS);
        assertThat(officialName.get("content").values())
                .extracting(player -> player.get("team").asString())
                .containsOnly(LIVERPOOL_NAME);
        assertThat(shortName.get("totalElements").asLong()).isZero();
        assertThat(shortName.get("content").values()).isEmpty();
    }

    @Test
    void unUsuarioComunRecibe403SinQueSeConsulteLaFuente() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri(SYNC_PATH).header(API_KEY_HEADER, user.apiKey()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body(result).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        verifyNoInteractions(adapter);
    }

    @Test
    void sinCredencialResponde401SinQueSeConsulteLaFuente() throws Exception {
        MvcTestResult result = mvc.post().uri(SYNC_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body(result).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        verifyNoInteractions(adapter);
    }

    @Test
    void unaSegundaCompletaIdenticaNoCreaNadaYCuentaTodoComoActualizado() throws Exception {
        synchronizeAs(adminToken);
        long totalAfterFirst = totalPlayers();
        JsonNode second = body(synchronizeAs(adminToken));
        long totalAfterSecond = totalPlayers();

        assertThat(second.get("leagues").values()).hasSize(League.values().length).allSatisfy(league -> {
            assertThat(league.get("teams").get("created").asInt()).isZero();
            assertThat(league.get("teams").get("updated").asInt()).isEqualTo(TEAMS_PER_LEAGUE);
            assertThat(league.get("players").get("created").asInt()).isZero();
            assertThat(league.get("players").get("updated").asInt()).isEqualTo(PLAYERS_PER_LEAGUE);
            assertThat(league.get("matches").get("created").asInt()).isZero();
            assertThat(league.get("matches").get("updated").asInt()).isEqualTo(MATCHES_PER_LEAGUE);
        });
        assertThat(totalAfterSecond)
                .isEqualTo(totalAfterFirst)
                .isEqualTo((long) PLAYERS_PER_LEAGUE * League.values().length);
    }

    private MvcTestResult synchronizeAs(String sessionToken) {
        return mvc.post().uri(SYNC_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + sessionToken).exchange();
    }

    private JsonNode playersOf(String teamName) throws Exception {
        return body(mvc.get().uri(PLAYERS_PATH).param("team", teamName).exchange());
    }

    private long totalPlayers() throws Exception {
        return body(mvc.get().uri(PLAYERS_PATH).exchange()).get("totalElements").asLong();
    }

    private static JsonNode playerNamed(JsonNode page, String name) {
        return page.get("content").valueStream()
                .filter(player -> player.get("name").asString().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
