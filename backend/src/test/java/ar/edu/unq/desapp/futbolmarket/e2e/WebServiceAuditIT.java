package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.shared.WebServiceAuditAspect;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La auditoría de web services de punta a punta: cada request real por MockMvc y las líneas que
 * quedan en la consola, leídas con {@link OutputCaptureExtension}. En el perfil test el logger
 * {@code WEB_SERVICE_AUDIT} escribe solo en la consola, con el formato del contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:web-service-audit-e2e;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ExtendWith(OutputCaptureExtension.class)
class WebServiceAuditIT {

    private static final String AUDIT_MARK = " | user=";
    private static final Pattern LINE_START = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2} \\| INFO \\| user=");
    private static final Pattern TIME = Pattern.compile(" \\| time=\\d+ms \\| ");
    private static final String CONTROLLER_PACKAGE = "ar.edu.unq.desapp.futbolmarket.controller";
    private static final int OWN_CONTROLLERS = 12;
    private static final int SIMULTANEOUS_REQUESTS = 10;
    private static final long FIRST_CONCURRENT_ID = 900_001L;
    private static final long MISSING_PLAYER = 999_999L;
    private static final long SUFFIX_MODULUS = 100_000_000L;
    private static final String PARAMS_MARK = " | params=";
    private static final String TIME_MARK = " | time=";
    private static final String KNOWN_PASSWORD = "secreto-de-prueba-011";
    private static final String NEW_PASSWORD = "otro-secreto-123";
    private static final String PLAYER_NOT_FOUND_MESSAGE = "No se encontró el jugador con id " + MISSING_PLAYER + ".";
    private static final String NOT_TRADABLE_MESSAGE = "El jugador no se puede operar: todavía no tiene cotización.";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";
    private static final String INVALID_REQUEST = "El request tiene datos inválidos.";
    private static final String UNREADABLE_BODY = "El cuerpo del request no es un JSON válido.";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PLAYERS_PATH = "/players";
    private static final String BUY_PATH = "/orders/buy";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private InitialOrderService initialOrderService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private LoggingSystem loggingSystem;

    private AuthTestHelper helper;
    private MarketDatabase database;
    private LogLevel rootLevel;
    private LogLevel auditLevel;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
        database = new MarketDatabase(context);
        database.clean();
        rootLevel = configuredLevel(LoggingSystem.ROOT_LOGGER_NAME);
        auditLevel = configuredLevel(WebServiceAuditAspect.WEB_SERVICE_AUDIT_LOGGER);
    }

    /** Deja los niveles como estaban, así los tests de US4 no afectan a los demás del mismo contexto. */
    @AfterEach
    void restore() {
        loggingSystem.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, rootLevel);
        loggingSystem.setLogLevel(WebServiceAuditAspect.WEB_SERVICE_AUDIT_LOGGER, auditLevel);
        database.clean();
    }

    // --- US1: cada invocación deja su línea ---

    @Test
    void unEndpointProtegidoConTokenDejaUnaLineaConLosSeisDatos(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        mvc.get().uri(portfolioPath(user)).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();

        List<String> lines = linesOf(output, "PortfolioController.getPortfolio");
        assertThat(lines).hasSize(1);
        String line = lines.getFirst();
        assertThat(LINE_START.matcher(line).find()).isTrue();
        assertThat(line)
                .contains(" | user=" + user.id() + "(USER,JWT) | ")
                .contains(" | op=PortfolioController.getPortfolio GET " + portfolioPath(user) + " | ")
                .contains("\"id\":" + user.id())
                .contains("\"date\":null")
                .endsWith(" | result=OK");
        assertThat(TIME.matcher(line).find()).isTrue();
    }

    @Test
    void conClaveDeApiElUsuarioDiceApiKeyYLaClaveNoAparece(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();

        mvc.get().uri(portfolioPath(user)).header(API_KEY_HEADER, user.apiKey()).exchange();

        List<String> lines = linesOf(output, "PortfolioController.getPortfolio");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains(" | user=" + user.id() + "(USER,API_KEY) | ");
        assertThat(auditLines(output)).noneMatch(line -> line.contains(user.apiKey()));
    }

    @Test
    void unaRutaPublicaConTokenQuedaComoAnonima(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        mvc.get().uri(PLAYERS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();

        List<String> lines = linesOf(output, "PlayerController.getPlayers");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains(" | user=anonymous | ");
    }

    @Test
    void losParametrosDeQueryVanConSuNombreYLosAusentesComoNulos(CapturedOutput output) {
        mvc.get().uri(PLAYERS_PATH + "?page=0&size=5&team=Bayern").exchange();

        List<String> lines = linesOf(output, "PlayerController.getPlayers");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains(" | op=PlayerController.getPlayers GET /players | ")
                .contains("\"page\":0")
                .contains("\"size\":5")
                .contains("\"team\":\"Bayern\"")
                .contains("\"league\":null");
    }

    @Test
    void elCuerpoDeUnaCompraQuedaEnLosParametros(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        buy(BEARER_PREFIX + token, "{\"playerId\": %d, \"quantity\": 10}".formatted(MISSING_PLAYER));

        List<String> lines = linesOf(output, "OrderController.buy");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains(" | op=OrderController.buy POST /orders/buy | ")
                .contains("\"request\":{\"playerId\":" + MISSING_PLAYER + ",\"quantity\":10}");
    }

    @Test
    void cadaInvocacionDejaSuPropiaLinea(CapturedOutput output) {
        mvc.get().uri(PLAYERS_PATH).exchange();
        mvc.get().uri(PLAYERS_PATH).exchange();
        mvc.get().uri(PLAYERS_PATH).exchange();

        assertThat(linesOf(output, "PlayerController.getPlayers")).hasSize(3);
    }

    @Test
    void lasInvocacionesSimultaneasDejanCadaUnaSuLineaCompleta(CapturedOutput output) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> requests = IntStream.range(0, SIMULTANEOUS_REQUESTS)
                .mapToObj(index -> (Callable<Integer>) () -> {
                    start.await();
                    return mvc.get().uri(PLAYERS_PATH + "/" + (FIRST_CONCURRENT_ID + index)).exchange()
                            .getResponse().getStatus();
                })
                .toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS)) {
            List<Future<Integer>> responses = requests.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> response : responses) {
                response.get();
            }
        }

        List<String> lines = linesOf(output, "PlayerController.getPlayer");
        assertThat(lines).hasSize(SIMULTANEOUS_REQUESTS);
        assertThat(lines).allSatisfy(line -> {
            assertThat(LINE_START.matcher(line).find()).isTrue();
            assertThat(TIME.matcher(line).find()).isTrue();
            assertThat(line).contains(" | result=");
        });
        IntStream.range(0, SIMULTANEOUS_REQUESTS).forEach(index -> {
            long id = FIRST_CONCURRENT_ID + index;
            assertThat(lines).filteredOn(line -> line.contains("GET /players/" + id + " | params={\"id\":" + id + "}"))
                    .hasSize(1);
        });
    }

    @Test
    void loQueNoLlegaAUnEndpointPropioNoDejaLinea(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        MvcTestResult health = mvc.get().uri("/actuator/health").exchange();
        MvcTestResult withoutCredential = mvc.get().uri("/auth/me").exchange();
        MvcTestResult missingRoute = mvc.get().uri("/ruta-inexistente")
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();

        assertThat(health.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(withoutCredential.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(missingRoute.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(auditLines(output))
                .hasSize(2)
                .allMatch(line -> line.contains("op=AuthController.register ") || line.contains("op=AuthController.login "));
    }

    @Test
    void todosLosControllersPropiosPasanPorElAspectoDeAuditoria() {
        Map<String, Object> controllers = context.getBeansWithAnnotation(RestController.class);

        List<Object> ownControllers = controllers.values().stream()
                .filter(bean -> AopUtils.getTargetClass(bean).getPackageName().startsWith(CONTROLLER_PACKAGE))
                .toList();

        assertThat(ownControllers).hasSize(OWN_CONTROLLERS);
        assertThat(ownControllers).allSatisfy(bean -> {
            assertThat(AopUtils.isAopProxy(bean)).isTrue();
            assertThat(((Advised) bean).getAdvisors())
                    .anySatisfy(advisor -> assertThat(isAuditAdvice(advisor.getAdvice())).isTrue());
        });
    }

    // --- US2: los datos sensibles nunca aparecen en claro ---

    @Test
    void elLoginRegistraLaContrasenaEnmascarada(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();

        String token = helper.login(user);

        List<String> lines = linesOf(output, "AuthController.login");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains(" | user=anonymous | ")
                .contains(" | op=AuthController.login POST /auth/login | ")
                .contains("\"username\":\"" + user.username() + "\"")
                .contains("\"password\":\"***\"");
        assertThat(output.getOut()).doesNotContain(user.password(), token);
    }

    @Test
    void elRegistroEnmascaraLaContrasenaYNoRegistraLaClaveQueDevuelve(CapturedOutput output) throws Exception {
        String username = "auditoria" + System.nanoTime() % SUFFIX_MODULUS;
        String email = username + "@correo.com";

        MvcTestResult result = mvc.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}"
                        .formatted(username, email, KNOWN_PASSWORD))
                .exchange();

        String apiKey = json(result).get("apiKey").asString();
        List<String> lines = linesOf(output, "AuthController.register");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains("\"username\":\"" + username + "\"")
                .contains("\"email\":\"" + email + "\"")
                .contains("\"password\":\"***\"");
        assertThat(output.getOut()).doesNotContain(KNOWN_PASSWORD, apiKey);
    }

    @Test
    void elCambioDeContrasenaEnmascaraLasDos(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        mvc.put().uri("/auth/me/password").header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(user.password(), NEW_PASSWORD))
                .exchange();

        List<String> lines = linesOf(output, "AccountController.changePassword");
        assertThat(lines).hasSize(1);
        assertThat(params(lines.getFirst()))
                .isEqualTo("{\"request\":{\"currentPassword\":\"***\",\"newPassword\":\"***\"}}");
        assertThat(output.getOut()).doesNotContain(user.password(), NEW_PASSWORD);
    }

    @Test
    void laRegeneracionDeLaClaveDeApiNoRegistraNiLaViejaNiLaNueva(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = mvc.post().uri("/auth/me/api-key").header(API_KEY_HEADER, user.apiKey()).exchange();

        String newApiKey = json(result).get("apiKey").asString();
        List<String> lines = linesOf(output, "AccountController.regenerateApiKey");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains(" | user=" + user.id() + "(USER,API_KEY) | ");
        assertThat(params(lines.getFirst())).isEqualTo("{}");
        assertThat(output.getOut()).doesNotContain(user.apiKey(), newApiKey);
    }

    @Test
    void laIdentidadDelUsuarioNoSeRepiteEnLosParametros(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        buy(BEARER_PREFIX + token, "{\"playerId\": %d, \"quantity\": 10}".formatted(MISSING_PLAYER));

        List<String> lines = linesOf(output, "OrderController.buy");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains(" | user=" + user.id() + "(USER,JWT) | ");
        assertThat(params(lines.getFirst()))
                .isEqualTo("{\"request\":{\"playerId\":" + MISSING_PLAYER + ",\"quantity\":10}}")
                .doesNotContain("userId");
    }

    @Test
    void ningunaLineaContieneLaCredencialDelRequest(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        mvc.get().uri(portfolioPath(user)).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
        mvc.get().uri("/auth/me").header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
        mvc.get().uri("/auth/me").header(API_KEY_HEADER, user.apiKey()).exchange();
        mvc.get().uri(PLAYERS_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .header(API_KEY_HEADER, user.apiKey()).exchange();
        buy(BEARER_PREFIX + token, "{\"playerId\": %d, \"quantity\": 1}".formatted(MISSING_PLAYER));

        assertThat(auditLines(output)).hasSize(7)
                .noneMatch(line -> line.contains(token) || line.contains(user.apiKey()));
    }

    // --- US3: las invocaciones que terminan en error también quedan ---

    @Test
    void unJugadorInexistenteRespondeLoMismoYDejaSuLineaConElTipoDeError(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(PLAYERS_PATH + "/" + MISSING_PLAYER).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(json(result).get("message").asString()).isEqualTo(PLAYER_NOT_FOUND_MESSAGE);
        List<String> lines = linesOf(output, "PlayerController.getPlayer");
        assertThat(lines).hasSize(1);
        String line = lines.getFirst();
        assertThat(LINE_START.matcher(line).find()).isTrue();
        assertThat(TIME.matcher(line).find()).isTrue();
        assertThat(line)
                .contains(" | user=anonymous | ")
                .contains(" | params={\"id\":" + MISSING_PLAYER + "} | ")
                .endsWith(" | result=ERROR(PlayerNotFoundException)")
                .doesNotContain(PLAYER_NOT_FOUND_MESSAGE);
    }

    @Test
    void unaCompraQueElDominioRechazaRespondeLoMismoYDejaSuLinea(CapturedOutput output) throws Exception {
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        initialOrderService.openMissing();
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        MvcTestResult result = buy(BEARER_PREFIX + token, "{\"playerId\": %d, \"quantity\": 10}".formatted(musiala.id()));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(result).get("message").asString()).isEqualTo(NOT_TRADABLE_MESSAGE);
        List<String> lines = linesOf(output, "OrderController.buy");
        assertThat(lines).hasSize(1);
        assertThat(TIME.matcher(lines.getFirst()).find()).isTrue();
        assertThat(lines.getFirst())
                .endsWith(" | result=ERROR(PlayerNotTradableException)")
                .doesNotContain(NOT_TRADABLE_MESSAGE);
    }

    @Test
    void unaRestriccionSobreUnParametroDejaSuLineaConElTipoDeError(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(PLAYERS_PATH + "/0").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(INVALID_PARAMETERS);
        List<String> lines = linesOf(output, "PlayerController.getPlayer");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains(" | params={\"id\":0} | ")
                .endsWith(" | result=ERROR(ConstraintViolationException)");
    }

    @Test
    void unCuerpoInvalidoOIlegibleRespondeLoMismoYNoDejaLinea(CapturedOutput output) throws Exception {
        MvcTestResult empty = login("{}");
        MvcTestResult malformed = login("{\"username\": ");

        assertThat(empty.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(empty).get("message").asString()).isEqualTo(INVALID_REQUEST);
        assertThat(malformed.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(malformed).get("message").asString()).isEqualTo(UNREADABLE_BODY);
        assertThat(linesOf(output, "AuthController.login")).isEmpty();
    }

    @Test
    void unParametroQueNoSeConvierteASuTipoRespondeLoMismoYNoDejaLinea(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri(PLAYERS_PATH + "/abc").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(INVALID_PARAMETERS);
        assertThat(auditLines(output)).isEmpty();
    }

    @Test
    void todasLasLineasVanEnInfoTambienLasQueTerminanEnError(CapturedOutput output) throws Exception {
        TestUser user = helper.registerUser();
        login("{\"username\": \"%s\", \"password\": \"incorrecta-123\"}".formatted(user.username()));
        mvc.get().uri(PLAYERS_PATH + "/" + MISSING_PLAYER).exchange();
        mvc.get().uri(PLAYERS_PATH + "/0").exchange();
        mvc.get().uri(PLAYERS_PATH).exchange();

        List<String> lines = auditLines(output);
        assertThat(lines).hasSize(5)
                .allMatch(line -> LINE_START.matcher(line).find())
                .anyMatch(line -> line.contains(" | result=ERROR("));
    }

    // --- US4: el registro se opera aparte del log general ---

    @Test
    void laLineaApareceUnaSolaVezYSinElFormatoDelLogGeneral(CapturedOutput output) {
        mvc.get().uri(PLAYERS_PATH).exchange();

        List<String> lines = output.getOut().lines()
                .filter(line -> line.contains(" | op=PlayerController.getPlayers "))
                .toList();
        assertThat(lines).hasSize(1);
        assertThat(LINE_START.matcher(lines.getFirst()).find()).isTrue();
        assertThat(lines.getFirst()).doesNotContain(WebServiceAuditAspect.WEB_SERVICE_AUDIT_LOGGER, " --- [");
    }

    @Test
    void conElNivelGeneralSubidoLaAuditoriaSigueEscribiendose(CapturedOutput output) {
        loggingSystem.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.WARN);

        mvc.get().uri(PLAYERS_PATH).exchange();

        assertThat(linesOf(output, "PlayerController.getPlayers")).hasSize(1);
    }

    @Test
    void apagandoElPropioRegistroNoSeEscribeNingunaLinea(CapturedOutput output) {
        loggingSystem.setLogLevel(WebServiceAuditAspect.WEB_SERVICE_AUDIT_LOGGER, LogLevel.OFF);

        mvc.get().uri(PLAYERS_PATH).exchange();

        assertThat(auditLines(output)).isEmpty();
    }

    // --- apoyo ---

    /** Las líneas de auditoría de la salida capturada, en orden. */
    private static List<String> auditLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains(AUDIT_MARK)).toList();
    }

    /** Las líneas de auditoría de una operación ({@code <Controller>.<método>}). */
    private static List<String> linesOf(CapturedOutput output, String operation) {
        return auditLines(output).stream().filter(line -> line.contains(" | op=" + operation + " ")).toList();
    }

    private LogLevel configuredLevel(String loggerName) {
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(loggerName);
        return configuration == null ? null : configuration.getConfiguredLevel();
    }

    /** Lo que quedó en {@code params} de una línea. */
    private static String params(String line) {
        return line.substring(line.indexOf(PARAMS_MARK) + PARAMS_MARK.length(), line.indexOf(TIME_MARK));
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static boolean isAuditAdvice(Advice advice) {
        return advice instanceof AbstractAspectJAdvice aspectJAdvice
                && aspectJAdvice.getAspectJAdviceMethod().getDeclaringClass().equals(WebServiceAuditAspect.class);
    }

    private static String portfolioPath(TestUser user) {
        return "/users/" + user.id() + "/portfolio";
    }

    private MvcTestResult login(String body) {
        return mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult buy(String authorization, String body) {
        return mvc.post().uri(BUY_PATH).header(AUTHORIZATION_HEADER, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
