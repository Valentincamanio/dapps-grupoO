package ar.edu.unq.desapp.futbolmarket.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.server.ResponseStatusException;

/**
 * El 400 de Actuator (por ejemplo, un nivel de log inválido) con el formato común y un mensaje fijo
 * (research D11 de la 012). Sin contexto de Spring, como {@code GlobalExceptionHandlerTest}.
 */
class GlobalExceptionHandlerResponseStatusTest {

    private static final String PATH = "/actuator/loggers/ar.edu.unq.desapp.futbolmarket";
    private static final String INTERNAL_REASON = "Failed to convert 'NOPE' to LogLevel";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), PATH);

    @Test
    void unBadRequestRespondeElFormatoComunConElMensajeFijoYSinElReasonInterno() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, INTERNAL_REASON);

        ResponseEntity<ApiError> response = handler.handleResponseStatus(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.error()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        assertThat(body.message()).isEqualTo(INVALID_PARAMETERS).doesNotContain(INTERNAL_REASON);
        assertThat(body.path()).isEqualTo(PATH);
        assertThat(body.violations()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"NOT_FOUND", "INTERNAL_SERVER_ERROR"})
    void otroStatusSeRelanzaSinTocar(HttpStatus status) {
        ResponseStatusException exception = new ResponseStatusException(status, INTERNAL_REASON);

        assertThatThrownBy(() -> handler.handleResponseStatus(exception, request)).isSameAs(exception);
    }

    @Test
    void unaResponseStatusExceptionLlegaAEsteHandlerYNoAlDeErroresInesperados() {
        Method method = resolver.resolveMethod(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("handleResponseStatus");
    }
}
