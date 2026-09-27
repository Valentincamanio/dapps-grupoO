package ar.edu.unq.desapp.futbolmarket.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe los errores que produce la cadena de filtros, que ocurren antes de llegar a un
 * controller y por eso el {@code @RestControllerAdvice} de {@code shared/} no ve (research D8).
 *
 * <p>Serializa con el {@code JsonMapper} de Boot, el mismo que usa el advice, así el formato del
 * {@code timestamp} coincide con el del resto de los errores de la API.</p>
 *
 * <p>Usa el {@code ApiError} de {@code shared/}: estos rechazos no son errores de validación, así
 * que {@code violations} viaja en {@code null} y no aparece en el cuerpo.</p>
 */
@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null));
    }
}
