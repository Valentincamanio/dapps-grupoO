package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
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
class AccountControllerIT {

    private static final String PROFILE_PATH = "/auth/me";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BCRYPT_PREFIX = "$2a$";
    private static final String EXPECTED_BALANCE_JSON = """
            "balance":1000.00""";

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
    }

    /** Cubre además los escenarios 1 y 2 de la HU3: las dos credenciales resuelven al mismo usuario. */
    @Test
    void conLaClaveYConElTokenDelMismoUsuarioDevuelveElMismoPerfil() throws Exception {
        TestUser user = helper.registerUser();
        String token = helper.login(user);

        MvcTestResult withApiKey = profileWithApiKey(user.apiKey());
        MvcTestResult withToken = profileWithToken(token);

        assertThat(withApiKey.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(withToken.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(withApiKey).get("id").asLong())
                .isEqualTo(body(withToken).get("id").asLong())
                .isEqualTo(user.id());
    }

    @Test
    void elPerfilTieneExactamenteLasClavesDelContratoYNingunSecreto() throws Exception {
        TestUser user = helper.registerUser();

        String rawBody = profileWithApiKey(user.apiKey()).getResponse().getContentAsString();

        assertThat(jsonMapper.readTree(rawBody).propertyNames())
                .containsExactlyInAnyOrder("id", "username", "email", "role", "balance");
        assertThat(rawBody)
                .doesNotContain(user.password())
                .doesNotContain(user.apiKey())
                .doesNotContain(BCRYPT_PREFIX);
    }

    /**
     * El saldo se comprueba sobre el JSON crudo: al releerlo con Jackson, los ceros finales de un
     * decimal se descartan y 1000.00 se vería como 1000.0.
     */
    @Test
    void elPerfilMuestraRolUsuarioYElSaldoInicial() throws Exception {
        TestUser user = helper.registerUser();

        String rawBody = profileWithApiKey(user.apiKey()).getResponse().getContentAsString();

        assertThat(jsonMapper.readTree(rawBody).get("role").asString()).isEqualTo(Role.USER.name());
        assertThat(rawBody).contains(EXPECTED_BALANCE_JSON);
    }

    @Test
    void cadaUsuarioVeSoloSusPropiosDatos() throws Exception {
        TestUser first = helper.registerUser();
        TestUser second = helper.registerUser();

        JsonNode firstProfile = body(profileWithApiKey(first.apiKey()));
        JsonNode secondProfile = body(profileWithToken(helper.login(second)));

        assertThat(firstProfile.get("id").asLong()).isEqualTo(first.id());
        assertThat(firstProfile.get("username").asString()).isEqualTo(first.username());
        assertThat(secondProfile.get("id").asLong()).isEqualTo(second.id());
        assertThat(secondProfile.get("username").asString()).isEqualTo(second.username());
        assertThat(firstProfile.get("id").asLong()).isNotEqualTo(secondProfile.get("id").asLong());
    }

    private MvcTestResult profileWithApiKey(String apiKey) {
        return mvc.get().uri(PROFILE_PATH).header(API_KEY_HEADER, apiKey).exchange();
    }

    private MvcTestResult profileWithToken(String token) {
        return mvc.get().uri(PROFILE_PATH).header(AUTHORIZATION_HEADER, BEARER_PREFIX + token).exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
