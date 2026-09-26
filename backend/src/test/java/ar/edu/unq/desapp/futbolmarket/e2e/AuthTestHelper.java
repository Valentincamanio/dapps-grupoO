package ar.edu.unq.desapp.futbolmarket.e2e;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra usuarios por HTTP para los tests end to end. Cada uno nace con datos únicos, así los
 * tests no dependen del orden de ejecución ni comparten estado.
 */
public class AuthTestHelper {

    private static final String REGISTER_PATH = "/auth/register";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String USERNAME_PREFIX = "user";
    private static final int SUFFIX_LENGTH = 8;
    private static final String PASSWORD = "campeon2022";
    private static final String EMAIL_DOMAIN = "@correo.com";

    private final MockMvcTester mvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AuthTestHelper(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public TestUser registerUser() throws Exception {
        String username = USERNAME_PREFIX + randomSuffix();
        String email = username + EMAIL_DOMAIN;

        MvcTestResult result = mvc.post().uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, email, PASSWORD))
                .exchange();

        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        return new TestUser(body.get("id").asLong(), username, email, PASSWORD,
                body.get("apiKey").asString());
    }

    /** Inicia sesión con las credenciales del usuario y devuelve el token de sesión. */
    public String login(TestUser user) throws Exception {
        MvcTestResult result = mvc.post().uri(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(user.username(), user.password()))
                .exchange();

        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    /** El sufijo es hexadecimal, así que respeta el patrón y el máximo de 30 del username. */
    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);
    }

    private String registerBody(String username, String email, String password) {
        return """
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, password);
    }

    private String loginBody(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    public record TestUser(Long id, String username, String email, String password, String apiKey) {
    }
}
