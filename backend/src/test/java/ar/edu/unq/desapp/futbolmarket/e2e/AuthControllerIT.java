package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    private static final String REGISTER_PATH = "/auth/register";
    private static final String PASSWORD = "campeon2022";
    private static final int API_KEY_LENGTH = 43;
    private static final String EXPECTED_BALANCE_JSON = """
            "balance":1000.00""";

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

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

    private String registerBody(String username, String email, String password) {
        return """
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, password);
    }
}
