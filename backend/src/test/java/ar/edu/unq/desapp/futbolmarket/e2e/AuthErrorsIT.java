package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Errores del registro y del login por HTTP, con el advice de {@code shared/} ya integrado.
 *
 * <p>Usa credenciales de admin al azar y una H2 propia, como {@code AdminAccountIT}: uno de los
 * casos necesita que el administrador exista para intentar registrarse con su nombre.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthErrorsIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    private static final String REGISTER_PATH = "/auth/register";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String PASSWORD = "campeon2022";
    private static final String SHORT_PASSWORD = "clave12";

    private static final String USERNAME_TAKEN = "El nombre de usuario ya está registrado.";
    private static final String EMAIL_TAKEN = "El correo electrónico ya está registrado.";
    private static final String BOTH_TAKEN = "El nombre de usuario y el correo electrónico ya están registrados.";
    private static final String INVALID_CREDENTIALS = "Credenciales inválidas.";

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:auth-errors-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvcTester mvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String unique;

    @BeforeEach
    void setUp() {
        unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    void unRegistroInvalidoDevuelveBadRequestConLasViolacionesYSinLaContrasena() throws Exception {
        MvcTestResult result = register("li", "correo-invalido", SHORT_PASSWORD);
        String rawBody = result.getResponse().getContentAsString();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(violationFields(rawBody)).contains("username", "email", "password");
        assertThat(rawBody).doesNotContain(SHORT_PASSWORD);

        JsonNode body = jsonMapper.readTree(rawBody);
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        assertThat(body.get("path").asString()).isEqualTo(REGISTER_PATH);
    }

    /** El registro corregido responde 201, así que el intento inválido no dejó ninguna cuenta. */
    @Test
    void elRegistroCorregidoSeCreaSinProblemas() throws Exception {
        register("li", "correo-invalido", SHORT_PASSWORD);

        MvcTestResult corrected = register("li_" + unique, "li_" + unique + "@correo.com", PASSWORD);

        assertThat(corrected.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(login("li", SHORT_PASSWORD).getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void unNombreDeUsuarioTomadoDevuelveConflictConSuMensaje() throws Exception {
        String username = "lionel_" + unique;
        register(username, "lionel_" + unique + "@correo.com", PASSWORD);

        assertConflict(register(username, "otro_" + unique + "@correo.com", PASSWORD), USERNAME_TAKEN);
    }

    @Test
    void unCorreoTomadoDevuelveConflictConSuMensaje() throws Exception {
        String email = "angel_" + unique + "@correo.com";
        register("angel_" + unique, email, PASSWORD);

        assertConflict(register("otro_" + unique, email, PASSWORD), EMAIL_TAKEN);
    }

    @Test
    void losDosTomadosDevuelvenConflictConElMensajeDeAmbos() throws Exception {
        String username = "emiliano_" + unique;
        String email = username + "@correo.com";
        register(username, email, PASSWORD);

        assertConflict(register(username, email, PASSWORD), BOTH_TAKEN);
    }

    /** El username y el correo se comparan recortados y sin distinguir mayúsculas. */
    @Test
    void elConflictoTambienSaltaConOtrasMayusculasOConEspacios() throws Exception {
        String username = "julian_" + unique;
        String email = username + "@correo.com";
        register(username, email, PASSWORD);

        assertConflict(register(username.toUpperCase(Locale.ROOT), "otro_" + unique + "@correo.com",
                PASSWORD), USERNAME_TAKEN);
        assertConflict(register("  " + username + "  ", "otro2_" + unique + "@correo.com", PASSWORD),
                USERNAME_TAKEN);
        assertConflict(register("otro3_" + unique, "  " + email + "  ", PASSWORD), EMAIL_TAKEN);
    }

    @Test
    void elRegistroPublicoConElNombreDelAdministradorDevuelveConflict() throws Exception {
        assertConflict(register(ADMIN_USERNAME, "otro_admin_" + unique + "@correo.com", PASSWORD),
                USERNAME_TAKEN);
    }

    /** SC-007: los dos rechazos son indistinguibles salvo por el timestamp. */
    @Test
    void elLoginRechazaIgualUnUsuarioInexistenteYUnaContrasenaIncorrecta() throws Exception {
        String username = "rodrigo_" + unique;
        register(username, username + "@correo.com", PASSWORD);

        MvcTestResult unknownUser = login("noexiste_" + unique, PASSWORD);
        MvcTestResult wrongPassword = login(username, "otraClave123");

        assertUnauthorized(unknownUser);
        assertUnauthorized(wrongPassword);
        assertThat(withoutTimestamp(unknownUser)).isEqualTo(withoutTimestamp(wrongPassword));
    }

    private void assertConflict(MvcTestResult result, String expectedMessage) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(expectedMessage);
        assertThat(body.get("path").asString()).isEqualTo(REGISTER_PATH);
    }

    private void assertUnauthorized(MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(INVALID_CREDENTIALS);
        assertThat(body.get("path").asString()).isEqualTo(LOGIN_PATH);
    }

    private List<String> withoutTimestamp(MvcTestResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        return List.of(
                String.valueOf(body.propertyNames()),
                body.get("status").asString(),
                body.get("error").asString(),
                body.get("message").asString(),
                body.get("path").asString());
    }

    private List<String> violationFields(String rawBody) {
        JsonNode violations = jsonMapper.readTree(rawBody).get("violations");
        return violations.valueStream().map(violation -> violation.get("field").asString()).toList();
    }

    private MvcTestResult register(String username, String email, String password) {
        return mvc.post().uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "email": "%s", "password": "%s"}
                        """.formatted(username, email, password))
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
