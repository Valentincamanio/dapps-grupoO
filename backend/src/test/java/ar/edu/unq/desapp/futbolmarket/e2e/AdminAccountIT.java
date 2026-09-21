package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.json.JsonMapper;

/**
 * El administrador de punta a punta: inicia sesión, ve su perfil y se emite una clave (escenarios
 * 5 y 6 de la HU7). Usa credenciales al azar y una H2 propia, igual que
 * {@code AdminAccountInitializerIT} (research D17).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String PROFILE_PATH = "/auth/me";
    private static final String API_KEY_PATH = "/auth/me/api-key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXPECTED_BALANCE_JSON = """
            "balance":0.00""";

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admin-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private TestUser admin;

    @BeforeEach
    void setUp() {
        helper = new AuthTestHelper(mvc);
        admin = new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null);
    }

    @Test
    void elAdministradorIniciaSesionConLaContrasenaConfigurada() throws Exception {
        assertThat(helper.login(admin)).isNotBlank();
    }

    /**
     * El saldo se comprueba sobre el JSON crudo: al releerlo con Jackson, los ceros finales de un
     * decimal se descartan y 0.00 se vería como 0.0.
     */
    @Test
    void elPerfilDelAdministradorMuestraRolAdminYSaldoCero() throws Exception {
        MvcTestResult result = mvc.get().uri(PROFILE_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + helper.login(admin))
                .exchange();
        String rawBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(jsonMapper.readTree(rawBody).get("role").asString()).isEqualTo(Role.ADMIN.name());
        assertThat(rawBody).contains(EXPECTED_BALANCE_JSON);
    }

    @Test
    void elAdministradorSeEmiteUnaClaveQueLeDaAcceso() throws Exception {
        MvcTestResult issued = mvc.post().uri(API_KEY_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + helper.login(admin))
                .exchange();
        String apiKey = jsonMapper.readTree(issued.getResponse().getContentAsString()).get("apiKey").asString();

        MvcTestResult withApiKey = mvc.get().uri(PROFILE_PATH).header(API_KEY_HEADER, apiKey).exchange();

        assertThat(issued.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(withApiKey.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(jsonMapper.readTree(withApiKey.getResponse().getContentAsString()).get("role").asString())
                .isEqualTo(Role.ADMIN.name());
    }
}
