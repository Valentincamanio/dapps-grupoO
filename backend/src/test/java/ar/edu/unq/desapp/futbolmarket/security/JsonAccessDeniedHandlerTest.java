package ar.edu.unq.desapp.futbolmarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El 403 no se puede provocar por HTTP en esta feature, porque todavía no hay ningún endpoint que
 * exija un rol. Por eso el handler se prueba directamente, sin contexto de Spring.
 */
class JsonAccessDeniedHandlerTest {

    private static final String PATH = "/auth/me";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final JsonAccessDeniedHandler handler =
            new JsonAccessDeniedHandler(new ApiErrorResponseWriter(jsonMapper));

    @Test
    void respondeForbiddenConElCuerpoDeError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("acceso denegado"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body.get("error").asString()).isEqualTo(HttpStatus.FORBIDDEN.getReasonPhrase());
        assertThat(body.get("message").asString()).isEqualTo("No tiene permisos para acceder a este recurso.");
        assertThat(body.get("path").asString()).isEqualTo(PATH);
    }
}
