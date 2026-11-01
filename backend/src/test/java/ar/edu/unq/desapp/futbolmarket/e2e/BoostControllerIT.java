package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.BoostSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los potenciados de punta a punta: el administrador los elige para la semana en curso o una próxima,
 * cualquiera ve los de la semana en curso, y los rechazos no guardan nada. Las semanas se calculan con
 * el reloj real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoostControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String CURRENT_PATH = "/players/boosts/current";
    private static final String ADMIN_PATH = "/quotes/boosts";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:boost-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BoostSQLDAO boostDAO;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private AuthTestHelper helper;
    private String adminToken;
    private QuoteWeek current;
    private List<Player> players;

    @BeforeEach
    void setUp() throws Exception {
        database = new QuoteDatabase(context);
        cleanDatabase();
        helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        current = QuoteWeek.containing(Instant.now());
        Team everton = database.team("62", "Everton FC");
        players = List.of(
                database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton),
                database.player("8136", "Beto", Position.FORWARD, everton),
                database.player("8137", "Jack Grealish", Position.MIDFIELDER, everton),
                database.player("8138", "James Tarkowski", Position.DEFENDER, everton));
    }

    @AfterEach
    void cleanDatabase() {
        boostDAO.deleteAll();
        database.clean();
    }

    @Test
    void sinPotenciadosLaSemanaEnCursoEsPublicaYVieneVacia() throws Exception {
        MvcTestResult result = mvc.get().uri(CURRENT_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode week = body(result);
        assertThat(week.get("weekStart").asString()).isEqualTo(current.monday().toString());
        assertThat(week.get("weekEnd").asString()).isEqualTo(current.monday().plusDays(6).toString());
        assertThat(week.get("status").asString()).isEqualTo("CURRENT");
        assertThat(week.get("players").isEmpty()).isTrue();
    }

    @Test
    void elAdministradorEligeLosDeLaSemanaEnCursoYCualquieraLosVe() throws Exception {
        MvcTestResult saved = put(current.monday().plusDays(2).toString(), fourPlayers(300), adminToken);

        MvcTestResult shown = mvc.get().uri(CURRENT_PATH).exchange();

        assertThat(saved.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode week = body(shown);
        assertThat(week.get("players").values())
                .extracting(player -> player.get("name").asString(), player -> player.get("percent").asInt())
                .containsExactly(
                        tuple("Iliman Ndiaye", 300),
                        tuple("Beto", 200),
                        tuple("Jack Grealish", 250),
                        tuple("James Tarkowski", 400));
        assertThat(week.get("players").get(0).get("team").asString()).isEqualTo("Everton FC");
    }

    @Test
    void unaSemanaProximaSeEligeSinCambiarLaEnCursoYSeConsultaComoAdministrador() throws Exception {
        String nextMonday = current.next().monday().toString();
        put(nextMonday, fourPlayers(225), adminToken);

        MvcTestResult next = get(ADMIN_PATH + "?week=" + nextMonday, adminToken);
        MvcTestResult shown = mvc.get().uri(CURRENT_PATH).exchange();

        assertThat(body(next).get("status").asString()).isEqualTo("UPCOMING");
        assertThat(body(next).get("players").values()).hasSize(4);
        assertThat(body(shown).get("players").isEmpty()).isTrue();
    }

    @Test
    void unaListaVaciaDejaLaSemanaSinPotenciados() throws Exception {
        put(current.monday().toString(), fourPlayers(300), adminToken);

        MvcTestResult result = put(current.monday().toString(), "{\"players\": []}", adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(boostDAO.count()).isZero();
    }

    @Test
    void unaSemanaCerradaRespondeConflictoSinGuardar() throws Exception {
        MvcTestResult result = put(current.previous().monday().toString(), fourPlayers(300), adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body(result).get("message").asString())
                .isEqualTo("La semana del " + current.previous().monday() + " ya cerró: sus potenciados no se pueden cambiar.");
        assertThat(boostDAO.count()).isZero();
    }

    @Test
    void reglasQueNoSeCumplenRespondenBadRequestSinGuardar() throws Exception {
        String monday = current.monday().toString();
        String three = "{\"players\": [" + boost(0, 300) + ", " + boost(1, 300) + ", " + boost(2, 300) + "]}";

        MvcTestResult tooFew = put(monday, three, adminToken);
        MvcTestResult tooMuch = put(monday, fourPlayers(450), adminToken);
        MvcTestResult withoutList = put(monday, "{}", adminToken);
        MvcTestResult badDate = put("2026-13-01", fourPlayers(300), adminToken);

        assertThat(tooFew.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(tooFew).get("message").asString())
                .isEqualTo("Una semana lleva exactamente 4 potenciados, o ninguno, y se eligieron 3.");
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(withoutList.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(badDate.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(boostDAO.count()).isZero();
    }

    @Test
    void unJugadorInexistenteRespondeNotFoundSinGuardar() throws Exception {
        String body = "{\"players\": [" + boost(0, 300) + ", " + boost(1, 300) + ", " + boost(2, 300)
                + ", {\"playerId\": 999999, \"percent\": 300}]}";

        MvcTestResult result = put(current.monday().toString(), body, adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(boostDAO.count()).isZero();
    }

    @Test
    void elegirlosOConsultarCualquierSemanaEsSoloDeAdministradores() throws Exception {
        String userToken = helper.login(helper.registerUser());
        String monday = current.monday().toString();

        MvcTestResult anonymousPut = mvc.put().uri(ADMIN_PATH + "/" + monday)
                .contentType(MediaType.APPLICATION_JSON).content(fourPlayers(300)).exchange();
        MvcTestResult userPut = put(monday, fourPlayers(300), userToken);
        MvcTestResult userGet = get(ADMIN_PATH, userToken);

        assertThat(anonymousPut.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(userPut.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(userGet.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(boostDAO.count()).isZero();
    }

    /**
     * Los cuatro jugadores: el primero con ese porcentaje y los demás con 200, 250 y 400.
     */
    private String fourPlayers(int firstPercent) {
        return "{\"players\": [" + boost(0, firstPercent) + ", " + boost(1, 200) + ", " + boost(2, 250) + ", "
                + boost(3, 400) + "]}";
    }

    private String boost(int index, int percent) {
        return "{\"playerId\": " + players.get(index).id() + ", \"percent\": " + percent + "}";
    }

    private MvcTestResult put(String week, String requestBody, String token) {
        return mvc.put().uri(ADMIN_PATH + "/" + week).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON).content(requestBody).exchange();
    }

    private MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
