package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cambio de nivel de log en caliente por {@code /actuator/loggers} (US3.6, FR-035 de la 012):
 * solo un {@code ADMIN} lo cambia, un nivel inválido responde el 400 del formato común y nada
 * cambia cuando el pedido se rechaza. Cada test deja el logger como estaba. Comparte el contexto
 * de {@link ActuatorITSupport} con los demás e2e de Actuator.
 */
class LoggersEndpointIT extends ActuatorITSupport {

    private static final String LOGGER_PATH = "/actuator/loggers/ar.edu.unq.desapp.futbolmarket";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEBUG = "DEBUG";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";

    private static final String DEBUG_BODY = """
            {"configuredLevel": "DEBUG"}
            """;
    private static final String INVALID_BODY = """
            {"configuredLevel": "NOPE"}
            """;
    private static final String RESET_BODY = """
            {"configuredLevel": null}
            """;

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        AuthTestHelper helper = new AuthTestHelper(mvc);
        adminToken = helper.login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
        userToken = helper.login(helper.registerUser());
    }

    @AfterEach
    void restoreLevel() {
        post(RESET_BODY, adminToken);
    }

    @Test
    void elAdministradorCambiaElNivelYElLoggerLoMuestra() throws Exception {
        MvcTestResult change = post(DEBUG_BODY, adminToken);

        assertThat(change.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(configuredLevel()).isEqualTo(DEBUG);
    }

    @Test
    void unNivelInvalidoRespondeElBadRequestComunYNoCambiaNada() throws Exception {
        String before = configuredLevel();

        MvcTestResult change = post(INVALID_BODY, adminToken);

        assertThat(change.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonNode body = jsonMapper.readTree(change.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(INVALID_PARAMETERS);
        assertThat(body.get("path").asString()).isEqualTo(LOGGER_PATH);
        assertThat(configuredLevel()).isEqualTo(before);
    }

    @Test
    void unUsuarioRecibe403YNoCambiaNada() throws Exception {
        String before = configuredLevel();

        MvcTestResult change = post(DEBUG_BODY, userToken);

        assertThat(change.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(configuredLevel()).isEqualTo(before);
    }

    @Test
    void unVisitanteRecibe401YNoCambiaNada() throws Exception {
        String before = configuredLevel();

        MvcTestResult change = mvc.post().uri(LOGGER_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(DEBUG_BODY).exchange();

        assertThat(change.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(configuredLevel()).isEqualTo(before);
    }

    // --- apoyo ---

    private MvcTestResult post(String body, String token) {
        return mvc.post().uri(LOGGER_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    /** El nivel configurado del logger del backend, o {@code null} si hereda. */
    private String configuredLevel() throws Exception {
        MvcTestResult result = mvc.get().uri(LOGGER_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken).exchange();
        JsonNode level = jsonMapper.readTree(result.getResponse().getContentAsString()).get("configuredLevel");
        return level == null || level.isNull() ? null : level.asString();
    }
}
