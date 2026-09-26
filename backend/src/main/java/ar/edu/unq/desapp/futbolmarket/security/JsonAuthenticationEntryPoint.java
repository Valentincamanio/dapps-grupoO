package ar.edu.unq.desapp.futbolmarket.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Responde 401 en JSON cuando falta la credencial o no es válida.
 *
 * <p>Ningún mensaje revela datos de otros usuarios ni cuál parte de la credencial falló
 * (FR-021): distingue solo entre token vencido, credencial inválida y credencial ausente.</p>
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String EXPIRED_TOKEN_MESSAGE = "El token de sesión está vencido.";
    private static final String INVALID_CREDENTIAL_MESSAGE = "La credencial es inválida.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                messageFor(authenticationException));
    }

    private String messageFor(AuthenticationException authenticationException) {
        if (authenticationException instanceof CredentialsExpiredException) {
            return EXPIRED_TOKEN_MESSAGE;
        }
        if (authenticationException instanceof BadCredentialsException) {
            return INVALID_CREDENTIAL_MESSAGE;
        }
        return MISSING_CREDENTIAL_MESSAGE;
    }
}
