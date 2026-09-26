package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

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

import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    private static final String REGISTER_PATH = "/auth/register";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String PASSWORD = "campeon2022";
    private static final int API_KEY_LENGTH = 43;
    private static final String EXPECTED_BALANCE_JSON = """
            "balance":1000.00""";
    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(24);
    private static final Duration TOLERANCE = Duration.ofSeconds(30);

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
    }

    @Test
    void elRegistroDevuelveCreatedConExactamenteLasClavesDelContrato() throws Exception {
        JsonNode body = register("lionel10", "lionel@correo.com", PASSWORD, HttpStatus.CREATED);

        assertThat(body.propertyNames())
                .containsExactlyInAnyOrder("id", "username", "email", "role", "balance", "apiKey");
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("username").asString()).isEqualTo("lionel10");
        assertThat(body.get("email").asString()).isEqualTo("lionel@correo.com");
        assertThat(body.get("apiKey").asString()).hasSize(API_KEY_LENGTH);
    }

    /**
     * El saldo se comprueba sobre el JSON crudo y no sobre el árbol releído: al volver a parsear,
     * Jackson descarta los ceros finales de un decimal y 1000.00 se vería como 1000.0. Lo que el
     * contrato fija son los dos decimales que viajan en la respuesta.
     */
    @Test
    void elRegistroAsignaRolUsuarioYElSaldoInicial() throws Exception {
        MvcTestResult result = post(registerBody("angel11", "angel@correo.com", PASSWORD));
        String rawBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(jsonMapper.readTree(rawBody).get("role").asString()).isEqualTo(Role.USER.name());
        assertThat(rawBody).contains(EXPECTED_BALANCE_JSON);
    }

    @Test
    void laRespuestaNoIncluyeLaContrasenaEnviada() throws Exception {
        MvcTestResult result = post(registerBody("emiliano01", "emiliano@correo.com", PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);
    }

    @Test
    void elRolDeclaradoPorElClienteSeIgnora() throws Exception {
        String requestBody = """
                {"username": "rodrigo07", "email": "rodrigo@correo.com", "password": "%s", "role": "ADMIN"}
                """.formatted(PASSWORD);

        MvcTestResult result = post(requestBody);
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(body.get("role").asString()).isEqualTo(Role.USER.name());
    }

    @Test
    void elNombreDeUsuarioYElCorreoVuelvenRecortados() throws Exception {
        JsonNode body = register("  julian09  ", "  julian@correo.com  ", PASSWORD, HttpStatus.CREATED);

        assertThat(body.get("username").asString()).isEqualTo("julian09");
        assertThat(body.get("email").asString()).isEqualTo("julian@correo.com");
    }

    @Test
    void elLoginDevuelveElTokenConTipoBearerYVencimientoAVeinticuatroHoras() throws Exception {
        TestUser user = helper.registerUser();
        Instant beforeLogin = Instant.now();

        MvcTestResult result = login(user.username(), user.password());
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("token", "tokenType", "expiresAt");
        assertThat(body.get("token").asString()).isNotBlank();
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(Instant.parse(body.get("expiresAt").asString())).isBetween(
                beforeLogin.plus(TOKEN_EXPIRATION).minus(TOLERANCE),
                Instant.now().plus(TOKEN_EXPIRATION).plus(TOLERANCE));
    }

    @Test
    void laRespuestaDelLoginNoIncluyeLaContrasenaNiLaClaveDeApi() throws Exception {
        TestUser user = helper.registerUser();

        String rawBody = login(user.username(), user.password()).getResponse().getContentAsString();

        assertThat(rawBody)
                .doesNotContain(user.password())
                .doesNotContain(user.apiKey());
    }

    @Test
    void elLoginAceptaElNombreDeUsuarioConOtrasMayusculas() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult result = login(user.username().toUpperCase(Locale.ROOT), user.password());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(helper.login(user)).isNotBlank();
    }

    private JsonNode register(String username, String email, String password, HttpStatus expectedStatus)
            throws Exception {
        MvcTestResult result = post(registerBody(username, email, password));

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus.value());
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcTestResult post(String requestBody) {
        return mvc.post().uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
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

    private String registerBody(String username, String email, String password) {
        return """
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, password);
    }
}
