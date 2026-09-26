package ar.edu.unq.desapp.futbolmarket.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Responde 403 en JSON cuando la credencial es válida pero no alcanza para el recurso pedido.
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        apiErrorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
    }
}
