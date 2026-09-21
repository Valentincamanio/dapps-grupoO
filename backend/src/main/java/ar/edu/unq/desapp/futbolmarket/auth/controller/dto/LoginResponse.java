package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;

/**
 * Respuesta del inicio de sesión. No lleva la contraseña ni la clave de API.
 */
public record LoginResponse(String token, String tokenType, Instant expiresAt) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static LoginResponse from(SessionToken sessionToken) {
        return new LoginResponse(sessionToken.value(), BEARER_TOKEN_TYPE, sessionToken.expiresAt());
    }
}
