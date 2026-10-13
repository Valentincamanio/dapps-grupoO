package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La aplicación sin la credencial de la fuente, que es como queda el perfil test: arranca igual,
 * el catálogo responde y el disparo manual responde 503 sin consultar la fuente (FR-041 y SC-012).
 * Usa un administrador al azar y una H2 propia, igual que {@code AdminAccountIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncDisabledIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String SYNC_PATH = "/players/sync";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DISABLED_MESSAGE = "La sincronización con Football-Data.org está deshabilitada: "
            + "falta configurar la credencial de la fuente (FOOTBALL_DATA_TOKEN).";

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:sync-disabled-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FootballDataAdapter adapter;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = new AuthTestHelper(mvc).login(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    @Test
    void sinTokenElDisparoDelAdministradorResponde503SinConsultarLaFuente() throws Exception {
        MvcTestResult result = mvc.post().uri(SYNC_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
                .exchange();
        JsonNode body = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(DISABLED_MESSAGE);
        assertThat(body.get("path").asString()).isEqualTo(SYNC_PATH);
        verifyNoInteractions(adapter);
    }

    @Test
    void sinTokenLaAplicacionArrancaYElCatalogoEstaVacioSinDatosFicticios() throws Exception {
        MvcTestResult result = mvc.get().uri("/players").exchange();
        JsonNode page = body(result);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(page.get("totalElements").asLong()).isZero();
        assertThat(page.get("content").values()).isEmpty();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
