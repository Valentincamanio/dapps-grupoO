package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

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

import ar.edu.unq.desapp.futbolmarket.config.JwtProperties;
import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Control de acceso de punta a punta. Usa {@code GET /actuator/info} como recurso protegido,
 * porque es el único que ya existe y exige credencial en esta fase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessControlIT {

    private static final String PROTECTED_PATH = "/actuator/info";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String SESSION_COOKIE = "JSESSIONID";

    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final String INVALID_CREDENTIAL_MESSAGE = "La credencial es inválida.";
    private static final String EXPIRED_TOKEN_MESSAGE = "El token de sesión está vencido.";

    private static final String UNKNOWN_API_KEY = "claveInventadaQueNoCorrespondeANingunUsuario";
    private static final String NOT_A_TOKEN = "abc";
    private static final long UNKNOWN_USER_ID = 999999L;
    private static final long VALID_EXPIRATION_SECONDS = 3600;
    private static final int SECRET_BYTES = 32;

    /**
     * Clave ajena a la aplicación, para firmar un token que tiene que rechazarse. Se genera en
     * cada corrida: el único secreto JWT escrito en el repositorio es el de los perfiles (SC-013).
     */
    private static final String OTHER_SECRET = randomSecret();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JwtProperties jwtProperties;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private TestUser user;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        helper = new AuthTestHelper(mvc);
        user = helper.registerUser();
        token = helper.login(user);
    }

    @Test
    void sinCredencialRespondeUnauthorizedEnJsonConLaFormaDeApiError() throws Exception {
        MvcTestResult result = mvc.get().uri(PROTECTED_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = body(result);
        assertThat(body.propertyNames())
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(body.get("path").asString()).isEqualTo(PROTECTED_PATH);
    }

    @Test
    void unaClaveDeApiInventadaRespondeCredencialInvalida() throws Exception {
        assertRejected(protectedWith(API_KEY_HEADER, UNKNOWN_API_KEY), INVALID_CREDENTIAL_MESSAGE);
    }

    @Test
    void unBearerQueNoEsUnTokenRespondeCredencialInvalida() throws Exception {
        assertRejected(protectedWith(AUTHORIZATION_HEADER, BEARER_PREFIX + NOT_A_TOKEN),
                INVALID_CREDENTIAL_MESSAGE);
    }

    /**
     * Los dos rechazos tienen que ser indistinguibles: la respuesta no puede dejar ver si el
     * problema fue la firma o que el usuario no existe (FR-021).
     */
    @Test
    void unJwtDeOtraClaveYUnJwtConIdInexistenteRespondenElMismoCuerpo() throws Exception {
        MvcTestResult foreignSignature = protectedWith(AUTHORIZATION_HEADER,
                BEARER_PREFIX + signedToken(String.valueOf(user.id()), OTHER_SECRET, validExpiration()));
        MvcTestResult unknownUser = protectedWith(AUTHORIZATION_HEADER,
                BEARER_PREFIX + signedToken(String.valueOf(UNKNOWN_USER_ID), jwtProperties.secret(),
                        validExpiration()));

        assertRejected(foreignSignature, INVALID_CREDENTIAL_MESSAGE);
        assertRejected(unknownUser, INVALID_CREDENTIAL_MESSAGE);
        assertThat(errorWithoutTimestamp(body(foreignSignature)))
                .isEqualTo(errorWithoutTimestamp(body(unknownUser)));
    }

    @Test
    void unJwtVencidoRespondeTokenVencido() throws Exception {
        String expired = signedToken(String.valueOf(user.id()), jwtProperties.secret(),
                Instant.now().minusSeconds(60));

        assertRejected(protectedWith(AUTHORIZATION_HEADER, BEARER_PREFIX + expired), EXPIRED_TOKEN_MESSAGE);
    }

    @Test
    void laClaveDeApiValidaDaAcceso() {
        assertThat(protectedWith(API_KEY_HEADER, user.apiKey()).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void elTokenValidoDaAcceso() {
        assertThat(protectedWith(AUTHORIZATION_HEADER, BEARER_PREFIX + token).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    /** Si llega un Bearer, la clave de API ni se mira, valga o no. */
    @Test
    void elJwtTienePrecedenciaSobreLaClaveDeApi() {
        MvcTestResult invalidJwtWithValidKey = mvc.get().uri(PROTECTED_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + NOT_A_TOKEN)
                .header(API_KEY_HEADER, user.apiKey())
                .exchange();
        MvcTestResult validJwtWithInvalidKey = mvc.get().uri(PROTECTED_PATH)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .header(API_KEY_HEADER, UNKNOWN_API_KEY)
                .exchange();

        assertThat(invalidJwtWithValidKey.getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(validJwtWithInvalidKey.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void unAuthorizationBasicSeIgnoraYPideCredencial() throws Exception {
        assertRejected(protectedWith(AUTHORIZATION_HEADER, "Basic bGlvbmVsMTA6Y2FtcGVvbjIwMjI="),
                MISSING_CREDENTIAL_MESSAGE);
    }

    @Test
    void ningunaRespuestaCreaUnaSesion() {
        MvcTestResult anonymous = mvc.get().uri(PROTECTED_PATH).exchange();
        MvcTestResult authenticated = protectedWith(API_KEY_HEADER, user.apiKey());

        assertThat(sessionCookies(anonymous)).isEmpty();
        assertThat(sessionCookies(authenticated)).isEmpty();
    }

    @Test
    void elHealthDeActuatorEsPublico() {
        assertThat(mvc.get().uri("/actuator/health").exchange().getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void laEspecificacionOpenApiEsPublicaYDeclaraLosDosEsquemas() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(result).get("components").get("securitySchemes").propertyNames())
                .contains("bearerAuth", "apiKeyAuth");
    }

    @Test
    void swaggerUiRedirigeASuIndice() {
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();

        assertThat(result.getResponse().getStatus()).isBetween(300, 399);
        assertThat(result.getResponse().getRedirectedUrl()).contains("/swagger-ui/index.html");
    }

    /** La excepción transitoria de FR-023 alcanza solo a los GET de consulta. */
    @Test
    void laConsultaDeJugadoresNoPideCredencialPeroLaCreacionSi() {
        assertThat(mvc.get().uri("/players").exchange().getResponse().getStatus())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(mvc.post().uri("/players").exchange().getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void unaCredencialInvalidaNoBloqueaElLogin() {
        MvcTestResult result = mvc.post().uri("/auth/login")
                .header(API_KEY_HEADER, UNKNOWN_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(user.username(), user.password()))
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void unaCredencialInvalidaNoBloqueaElRegistro() {
        String username = "pub" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MvcTestResult result = mvc.post().uri("/auth/register")
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + NOT_A_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username))
                .exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
    }

    /** La consola de H2 es pública solo en el perfil local. */
    @Test
    void laConsolaDeH2NoEsPublicaEnTest() {
        assertThat(mvc.get().uri("/h2-console").exchange().getResponse().getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private MvcTestResult protectedWith(String headerName, String headerValue) {
        return mvc.get().uri(PROTECTED_PATH).header(headerName, headerValue).exchange();
    }

    private void assertRejected(MvcTestResult result, String expectedMessage) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body(result).get("message").asString()).isEqualTo(expectedMessage);
    }

    /**
     * El {@code timestamp} es lo único que puede diferir entre dos rechazos equivalentes, así que
     * se compara el resto del cuerpo campo por campo, junto con el conjunto de claves.
     */
    private List<String> errorWithoutTimestamp(JsonNode body) {
        return List.of(
                String.valueOf(body.propertyNames()),
                body.get("status").asString(),
                body.get("error").asString(),
                body.get("message").asString(),
                body.get("path").asString());
    }

    private List<String> sessionCookies(MvcTestResult result) {
        return result.getResponse().getHeaders(SET_COOKIE_HEADER).stream()
                .filter(header -> header.contains(SESSION_COOKIE))
                .toList();
    }

    private Instant validExpiration() {
        return Instant.now().plusSeconds(VALID_EXPIRATION_SECONDS);
    }

    private String signedToken(String subject, String base64Secret, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String loginBody(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    private String registerBody(String username) {
        return """
                {"username": "%s", "email": "%s@correo.com", "password": "campeon2022"}
                """.formatted(username, username);
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String randomSecret() {
        byte[] material = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(material);
        return Base64.getEncoder().encodeToString(material);
    }
}
