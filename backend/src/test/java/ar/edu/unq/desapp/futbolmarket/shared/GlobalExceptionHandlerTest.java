package ar.edu.unq.desapp.futbolmarket.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

/**
 * El manejador genérico decide si una excepción le corresponde o si tiene que seguir de largo, y
 * esa decisión no se puede provocar por HTTP: las excepciones de la cadena de filtros nunca llegan
 * al advice. Por eso se lo prueba directamente, sin contexto de Spring.
 */
class GlobalExceptionHandlerTest {

    private static final String PATH = "/auth/me";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void respondeErrorInternoSinFiltrarElDetalleDeLaExcepcion() throws Exception {
        var request = requestDe(PATH);

        ResponseEntity<ApiError> respuesta =
                handler.handleUnexpected(new IllegalStateException("fallo interno con datos sensibles"), request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ApiError cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(cuerpo.error()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        assertThat(cuerpo.message()).isEqualTo("Ocurrió un error inesperado.");
        assertThat(cuerpo.path()).isEqualTo(PATH);
        assertThat(cuerpo.violations()).isNull();
        assertThat(cuerpo.timestamp()).isNotNull();
        assertThat(cuerpo.message()).doesNotContain("fallo interno con datos sensibles");
    }

    @Test
    void relanzaLasExcepcionesDeAutenticacion() {
        var excepcion = new BadCredentialsException("credencial inválida");

        assertThatThrownBy(() -> handler.handleUnexpected(excepcion, requestDe(PATH)))
                .isSameAs(excepcion);
    }

    @Test
    void relanzaLasExcepcionesDeAutorizacion() {
        var excepcion = new AccessDeniedException("acceso denegado");

        assertThatThrownBy(() -> handler.handleUnexpected(excepcion, requestDe(PATH)))
                .isSameAs(excepcion);
    }

    @Test
    void relanzaLasQueSpringYaTraduceASuPropioStatus() {
        var excepcion = new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "método no soportado");

        assertThatThrownBy(() -> handler.handleUnexpected(excepcion, requestDe(PATH)))
                .isSameAs(excepcion);
    }

    private MockHttpServletRequest requestDe(String path) {
        return new MockHttpServletRequest(HttpMethod.GET.name(), path);
    }
}
