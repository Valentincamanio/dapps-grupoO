package ar.edu.unq.desapp.futbolmarket.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * La base nueva del 403 de dominio. Como en {@code GlobalExceptionHandlerTest}, sin contexto de
 * Spring: el handler se elige con el mismo resolver que usa Spring MVC, así se prueba también que
 * una excepción de dominio llega al handler de su base y no a otro.
 */
class GlobalExceptionHandlerForbiddenTest {

    private static final String PATH = "/orders/77/cancel";
    private static final String MESSAGE = "La orden no pertenece al usuario.";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    @Test
    void unaExcepcionProhibidaDelDominioRespondeForbiddenConElFormatoComunYSuMensaje() {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), PATH);

        ResponseEntity<ApiError> response = handler.handleForbiddenException(new SampleForbiddenException(MESSAGE), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body.error()).isEqualTo(HttpStatus.FORBIDDEN.getReasonPhrase());
        assertThat(body.message()).isEqualTo(MESSAGE);
        assertThat(body.path()).isEqualTo(PATH);
        assertThat(body.violations()).isNull();
        assertThat(body.timestamp()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("excepcionesDeCadaBase")
    void cadaBaseLlegaASuHandlerYRespondeSuStatus(RuntimeException exception, HttpStatus expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), PATH);

        Method method = resolver.resolveMethod(exception);
        ResponseEntity<?> response = (ResponseEntity<?>) method.invoke(handler, exception, request);

        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isInstanceOf(ApiError.class);
        assertThat(((ApiError) response.getBody()).message()).isEqualTo(exception.getMessage());
    }

    static Stream<Arguments> excepcionesDeCadaBase() {
        return Stream.of(
                Arguments.of(new SampleForbiddenException(MESSAGE), HttpStatus.FORBIDDEN),
                Arguments.of(new BadRequestException("La fecha no es válida."), HttpStatus.BAD_REQUEST),
                Arguments.of(new UnauthorizedException("Las credenciales no son válidas."), HttpStatus.UNAUTHORIZED),
                Arguments.of(new NotFoundException("No se encontró la orden con id 77."), HttpStatus.NOT_FOUND),
                Arguments.of(new ConflictException("La orden ya está ejecutada."), HttpStatus.CONFLICT),
                Arguments.of(new ServiceUnavailableException("El servicio no está disponible."),
                        HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * Una excepción de dominio cualquiera que extiende la base, como lo hacen las del mercado.
     */
    private static final class SampleForbiddenException extends ForbiddenException {
        private SampleForbiddenException(String message) {
            super(message);
        }
    }
}
