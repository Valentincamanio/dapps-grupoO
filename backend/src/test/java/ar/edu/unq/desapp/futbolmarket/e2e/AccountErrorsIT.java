package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rechazos del cambio de contraseña por HTTP, con el advice de {@code shared/} ya integrado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountErrorsIT {

    private static final String PASSWORD_PATH = "/auth/me/password";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String NEW_PASSWORD = "tricampeon2022";
    private static final String SHORT_PASSWORD = "clave12";
    private static final String WRONG_PASSWORD = "otraClave123";

    private static final String WRONG_CURRENT_PASSWORD = "La contraseña actual es incorrecta.";
    private static final String SAME_PASSWORD = "La nueva contraseña debe ser distinta de la actual.";

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
    }

    @Test
    void conLaContrasenaActualIncorrectaDevuelveBadRequestYLaVigenteSigueSirviendo() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = changePassword(user, WRONG_PASSWORD, NEW_PASSWORD);

        assertRejected(result, WRONG_CURRENT_PASSWORD);
        assertThat(login(user.username(), user.password()).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void conLaNuevaIgualALaActualDevuelveBadRequest() throws Exception {
        TestUser user = helper.registerUser();

        assertRejected(changePassword(user, user.password(), user.password()), SAME_PASSWORD);
    }

    /** Las violaciones nombran el campo, pero nunca traen el valor enviado (FR-009 y SC-003). */
    @Test
    void conLaNuevaDeSieteCaracteresDevuelveBadRequestSinLosValoresDeLasContrasenas() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = changePassword(user, user.password(), SHORT_PASSWORD);
        String rawBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(violationFields(rawBody)).contains("newPassword");
        assertThat(rawBody)
                .doesNotContain(SHORT_PASSWORD)
                .doesNotContain(user.password());
    }

    /** Escenario 2 de la HU5: la contraseña anterior deja de servir. */
    @Test
    void trasUnCambioExitosoElLoginConLaAnteriorDevuelveUnauthorized() throws Exception {
        TestUser user = helper.registerUser();

        assertThat(changePassword(user, user.password(), NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(HttpStatus.NO_CONTENT.value());

        MvcTestResult withOldPassword = login(user.username(), user.password());
        JsonNode body = jsonMapper.readTree(withOldPassword.getResponse().getContentAsString());

        assertThat(withOldPassword.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo("Credenciales inválidas.");
        assertThat(login(user.username(), NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    private void assertRejected(MvcTestResult result, String expectedMessage) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(expectedMessage);
        assertThat(body.get("path").asString()).isEqualTo(PASSWORD_PATH);
    }

    private List<String> violationFields(String rawBody) {
        JsonNode violations = jsonMapper.readTree(rawBody).get("violations");
        return violations.valueStream().map(violation -> violation.get("field").asString()).toList();
    }

    private MvcTestResult changePassword(TestUser user, String currentPassword, String newPassword) {
        return mvc.put().uri(PASSWORD_PATH)
                .header(API_KEY_HEADER, user.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword))
                .exchange();
    }

    private MvcTestResult login(String username, String password) {
        return mvc.post().uri(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password))
                .exchange();
    }
}
