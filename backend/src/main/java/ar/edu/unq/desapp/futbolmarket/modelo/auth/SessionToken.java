package ar.edu.unq.desapp.futbolmarket.modelo.auth;

import java.time.Instant;

/**
 * Token de sesión emitido al iniciar sesión. Vale hasta {@code expiresAt} aunque después cambien
 * la contraseña o la clave de API: no hay lista negra ni versionado (FR-035).
 */
public record SessionToken(String value, Instant expiresAt) {
}
