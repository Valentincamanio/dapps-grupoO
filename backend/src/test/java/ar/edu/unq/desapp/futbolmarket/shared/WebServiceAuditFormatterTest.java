package ar.edu.unq.desapp.futbolmarket.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las reglas de cada campo de la línea de auditoría de web services, sin Spring: el formatter se
 * construye con {@code new} y las invocaciones se arman a mano sobre un controller de prueba.
 */
class WebServiceAuditFormatterTest {

    private static final String TRUNCATION_MARK = "...(truncado)";
    private static final int MAX_VALUE_LENGTH = 500;
    private static final int MAX_PARAMS_LENGTH = 2000;

    private final WebServiceAuditFormatter formatter = new WebServiceAuditFormatter(JsonMapper.builder().build());

    // --- user ---

    @Test
    void sinAutenticacionElUsuarioEsAnonimo() {
        String user = formatter.user(null);

        assertThat(user).isEqualTo("anonymous");
    }

    @Test
    void conAutenticacionAnonimaElUsuarioEsAnonimo() {
        Authentication authentication = new AnonymousAuthenticationToken(
                "clave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("anonymous");
    }

    @Test
    void conUnaAutenticacionNoAutenticadaElUsuarioEsAnonimo() {
        Authentication authentication = UsernamePasswordAuthenticationToken.unauthenticated(42L, null);

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("anonymous");
    }

    @Test
    void conTokenDeSesionElUsuarioLlevaIdRolYJwt() {
        Authentication authentication = authenticated(42L, "ROLE_USER", CredentialType.JWT);

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("42(USER,JWT)");
    }

    @Test
    void conClaveDeApiElUsuarioLlevaIdRolYApiKey() {
        Authentication authentication = authenticated(7L, "ROLE_ADMIN", CredentialType.API_KEY);

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("7(ADMIN,API_KEY)");
    }

    @Test
    void sinTipoDeCredencialElUsuarioLlevaSoloElRol() {
        Authentication authentication = authenticated(42L, "ROLE_USER", null);

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("42(USER)");
    }

    @Test
    void sinAuthorityDeRolElRolEsDesconocido() {
        Authentication authentication = authenticated(42L, "SCOPE_read", CredentialType.JWT);

        String user = formatter.user(authentication);

        assertThat(user).isEqualTo("42(?,JWT)");
    }

    // --- op ---

    @Test
    void laOperacionEsElControllerYElMetodo() {
        WebServiceInvocation invocation = invocation("getThing", 42L, "x", null);

        String operation = formatter.operation(invocation);

        assertThat(operation).isEqualTo("FakeController.getThing");
    }

    @Test
    void sinRequestElVerboYLaUriSonGuiones() {
        WebServiceInvocation invocation = withRequest(null, null);

        String httpMethod = formatter.httpMethod(invocation);
        String requestUri = formatter.requestUri(invocation);

        assertThat(httpMethod).isEqualTo("-");
        assertThat(requestUri).isEqualTo("-");
    }

    @Test
    void laUriConCaracteresDeControlQuedaEscapadaEnUnaLinea() {
        WebServiceInvocation invocation = withRequest("GET", "/things/1\nINFO falso\r\t\u0001\u007F");

        String requestUri = formatter.requestUri(invocation);

        assertThat(requestUri).isEqualTo("/things/1\\nINFO falso\\r\\t\\u0001\\u007F");
        assertThat(requestUri).doesNotContain("\n", "\r", "\t");
    }

    // --- params ---

    @Test
    void losParametrosVanConSuNombreYUnNuloExplicito() {
        WebServiceInvocation invocation = invocation("getThing", 42L, "x", null);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"id\":42,\"name\":\"x\",\"date\":null}");
    }

    @Test
    void unRecordAnidadoConUnaListaSeSerializaCompleto() {
        ThingRequest request = new ThingRequest("x", List.of(new Item("a", 1), new Item("b", 2)));
        WebServiceInvocation invocation = invocation("withRecord", request);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo(
                "{\"request\":{\"name\":\"x\",\"items\":[{\"code\":\"a\",\"amount\":1},{\"code\":\"b\",\"amount\":2}]}}");
    }

    @Test
    void unMetodoSinArgumentosDaUnObjetoVacio() {
        WebServiceInvocation invocation = invocation("noArguments");

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{}");
    }

    @Test
    void elRequestDelServletYElBindingResultNoSeRegistran() {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        WebServiceInvocation invocation = invocation("withServletArguments",
                42L, new MockHttpServletRequest("GET", "/things/42"), bindingResult);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"id\":42}");
    }

    @Test
    void unValorDeMasDeQuinientosCaracteresSeTrunca() {
        String text = "a".repeat(MAX_VALUE_LENGTH + 1);
        WebServiceInvocation invocation = invocation("withText", text);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"text\":\"" + "a".repeat(MAX_VALUE_LENGTH) + TRUNCATION_MARK + "\"}");
    }

    @Test
    void unValorDeQuinientosCaracteresSaleEntero() {
        String text = "a".repeat(MAX_VALUE_LENGTH);
        WebServiceInvocation invocation = invocation("withText", text);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"text\":\"" + text + "\"}");
    }

    @Test
    void losValoresDeUnaListaTambienSeTruncan() {
        String text = "b".repeat(MAX_VALUE_LENGTH);
        WebServiceInvocation invocation = invocation("withList", List.of(text, text + "b"));

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo(
                "{\"values\":[\"" + text + "\",\"" + text + TRUNCATION_MARK + "\"]}");
    }

    @Test
    void unTotalDeMasDeDosMilCaracteresSeCortaConLaMarca() {
        String text = "c".repeat(MAX_VALUE_LENGTH);
        WebServiceInvocation invocation = invocation("withList", List.of(text, text, text, text, text));

        String params = formatter.params(invocation);

        assertThat(params).hasSize(MAX_PARAMS_LENGTH + TRUNCATION_MARK.length());
        assertThat(params).startsWith("{\"values\":[\"ccc");
        assertThat(params).endsWith(TRUNCATION_MARK);
    }

    @Test
    void losSaltosDeLineaYTabulacionesDeUnValorQuedanEscapados() {
        WebServiceInvocation invocation = invocation("withText", "uno\ndos\rtres\tcuatro");

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"text\":\"uno\\ndos\\rtres\\tcuatro\"}");
        assertThat(params).doesNotContain("\n", "\r", "\t");
    }

    @Test
    void unArgumentoQueNoSePuedeSerializarQuedaConElMarcadorYElRestoSeEscribe() {
        WebServiceInvocation invocation = invocation("withBrokenValue", new Unreadable(), "x");

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"value\":\"<no representable>\",\"name\":\"x\"}");
    }

    // --- enmascarado e identidad ---

    @Test
    void lasContrasenasDePrimerNivelDeUnRecordSeEnmascaran() {
        ChangePassword request = new ChangePassword("actual-secreta", "nueva-secreta");
        WebServiceInvocation invocation = invocation("withChangePassword", request);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"request\":{\"currentPassword\":\"***\",\"newPassword\":\"***\"}}");
    }

    @Test
    void elLoginEnmascaraLaContrasenaYDejaElUsuarioYElEmailEnClaro() {
        Credentials request = new Credentials("messi10", "messi10@correo.com", "secreto-de-prueba");
        WebServiceInvocation invocation = invocation("withCredentials", request);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo(
                "{\"request\":{\"username\":\"messi10\",\"email\":\"messi10@correo.com\",\"password\":\"***\"}}");
        assertThat(params).doesNotContain("secreto-de-prueba");
    }

    @Test
    void tokensClavesDeApiYSecretosSeEnmascaranConCualquierEscritura() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("token", "t-secreto");
        headers.put("apiKey", "k-secreto");
        headers.put("api_key", "k2-secreto");
        headers.put("X-Api-Key", "k3-secreto");
        headers.put("clientSecret", "s-secreto");
        headers.put("name", "visible");
        WebServiceInvocation invocation = invocation("withMap", headers);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"values\":{\"token\":\"***\",\"apiKey\":\"***\",\"api_key\":\"***\","
                + "\"X-Api-Key\":\"***\",\"clientSecret\":\"***\",\"name\":\"visible\"}}");
        assertThat(params).doesNotContain("-secreto");
    }

    @Test
    void unSecretoAnidadoYDentroDeUnaListaSeEnmascara() {
        Outer request = new Outer("x", new Inner("p-secreta", "a"), List.of(new Inner("q-secreta", "b")));
        WebServiceInvocation invocation = invocation("withOuter", request);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"request\":{\"name\":\"x\",\"inner\":{\"password\":\"***\",\"code\":\"a\"},"
                + "\"inners\":[{\"password\":\"***\",\"code\":\"b\"}]}}");
    }

    @Test
    void unSecretoNuloVacioNumericoOCompuestoTambienSaleEnmascarado() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("password", null);
        values.put("token", "");
        values.put("secret", 1234);
        values.put("apiKeys", List.of("a", "b"));
        WebServiceInvocation invocation = invocation("withMap", values);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo(
                "{\"values\":{\"password\":\"***\",\"token\":\"***\",\"secret\":\"***\",\"apiKeys\":\"***\"}}");
    }

    @Test
    void unParametroDeQueryQueSeLlamaTokenSeEnmascara() {
        WebServiceInvocation invocation = invocation("withToken", "t-secreto");

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"token\":\"***\"}");
    }

    @Test
    void unSecretoLargoSeEnmascaraSinTruncar() {
        WebServiceInvocation invocation = invocation("withToken", "x".repeat(600));

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"token\":\"***\"}");
        assertThat(params).doesNotContain(TRUNCATION_MARK);
    }

    @Test
    void elPrincipalYLaAutenticacionNoSeRegistran() {
        Authentication authentication = authenticated(42L, "ROLE_USER", CredentialType.JWT);
        WebServiceInvocation invocation = invocation("withPrincipal", 42L, 42L, authentication, 7L);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{\"id\":7}");
    }

    @Test
    void unMetodoConSoloElPrincipalDaUnObjetoVacio() {
        WebServiceInvocation invocation = invocation("withOnlyPrincipal", 42L);

        String params = formatter.params(invocation);

        assertThat(params).isEqualTo("{}");
    }

    // --- time y result ---

    @Test
    void elTiempoSeInformaEnMilisegundosEnteros() {
        WebServiceInvocation invocation = finished(12_500_000L, null, true);

        long elapsedMillis = formatter.elapsedMillis(invocation);

        assertThat(elapsedMillis).isEqualTo(12L);
    }

    @Test
    void unaInvocacionQueVolvioTerminaOk() {
        WebServiceInvocation invocation = finished(0L, null, true);

        String result = formatter.result(invocation);

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void unaInvocacionQueLanzoUnaExcepcionTerminaConSuTipoYSinElMensaje() {
        WebServiceInvocation invocation = finished(0L, new IllegalStateException("mensaje con datos"), false);

        String result = formatter.result(invocation);

        assertThat(result).isEqualTo("ERROR(IllegalStateException)");
        assertThat(result).doesNotContain("mensaje con datos");
    }

    @Test
    void unaExcepcionPropiaTerminaConSuNombreSimple() {
        WebServiceInvocation invocation = finished(0L, new ThingNotFoundException(), false);

        String result = formatter.result(invocation);

        assertThat(result).isEqualTo("ERROR(ThingNotFoundException)");
    }

    @Test
    void sinCompletarYSinExcepcionTerminaEnErrorSinTipo() {
        WebServiceInvocation invocation = finished(0L, null, false);

        String result = formatter.result(invocation);

        assertThat(result).isEqualTo("ERROR");
    }

    @Test
    void completadaYSinExcepcionTerminaOk() {
        WebServiceInvocation invocation = finished(0L, null, true);

        String result = formatter.result(invocation);

        assertThat(result).isEqualTo("OK");
    }

    // --- apoyo ---

    private static Authentication authenticated(Long id, String authority, CredentialType credentialType) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                id, null, List.of(new SimpleGrantedAuthority(authority)));
        authentication.setDetails(credentialType);
        return authentication;
    }

    private static WebServiceInvocation invocation(String methodName, Object... arguments) {
        return new WebServiceInvocation(null, FakeController.class, method(methodName), Arrays.asList(arguments),
                "GET", "/things", 0L, null, true);
    }

    private static WebServiceInvocation withRequest(String httpMethod, String requestUri) {
        return new WebServiceInvocation(null, FakeController.class, method("noArguments"), List.of(),
                httpMethod, requestUri, 0L, null, true);
    }

    private static WebServiceInvocation finished(long elapsedNanos, RuntimeException failure, boolean completed) {
        return new WebServiceInvocation(null, FakeController.class, method("noArguments"), List.of(),
                "GET", "/things", elapsedNanos, failure, completed);
    }

    private static Method method(String name) {
        return Arrays.stream(FakeController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** Las formas de endpoint que hacen falta para los casos; no se invocan nunca. */
    @SuppressWarnings("unused")
    static class FakeController {

        public void getThing(Long id, String name, String date) {
        }

        public void withRecord(ThingRequest request) {
        }

        public void noArguments() {
        }

        public void withServletArguments(Long id, HttpServletRequest request, BindingResult bindingResult) {
        }

        public void withText(String text) {
        }

        public void withList(List<String> values) {
        }

        public void withBrokenValue(Object value, String name) {
        }

        public void withChangePassword(ChangePassword request) {
        }

        public void withCredentials(Credentials request) {
        }

        public void withMap(Map<String, Object> values) {
        }

        public void withOuter(Outer request) {
        }

        public void withToken(String token) {
        }

        public void withPrincipal(@AuthenticationPrincipal Long userId, @CurrentUser Long requesterId,
                                  Authentication authentication, Long id) {
        }

        public void withOnlyPrincipal(@AuthenticationPrincipal Long userId) {
        }
    }

    /** Tiene {@code @AuthenticationPrincipal} como meta-anotación. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @AuthenticationPrincipal
    @interface CurrentUser {
    }

    record ThingRequest(String name, List<Item> items) {
    }

    record Item(String code, int amount) {
    }

    record ChangePassword(String currentPassword, String newPassword) {
    }

    record Credentials(String username, String email, String password) {
    }

    record Outer(String name, Inner inner, List<Inner> inners) {
    }

    record Inner(String password, String code) {
    }

    static class ThingNotFoundException extends RuntimeException {

        ThingNotFoundException() {
            super("No se encontró la cosa con id 42.");
        }
    }

    /** Jackson no lo puede serializar: su único getter falla. */
    static class Unreadable {

        public String getValue() {
            throw new IllegalStateException("no se puede leer");
        }
    }
}
