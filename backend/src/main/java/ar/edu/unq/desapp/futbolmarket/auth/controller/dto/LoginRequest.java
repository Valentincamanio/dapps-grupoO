package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.CredentialPolicy;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del inicio de sesión.
 *
 * <p>No repite las reglas de formato del registro a propósito: un 400 por largo o por patrón
 * delataría cómo son las credenciales guardadas. Lo único que se exige es que los dos campos
 * vengan. El username se recorta; la contraseña, nunca.</p>
 */
public record LoginRequest(

        @NotBlank(message = CredentialPolicy.USERNAME_REQUIRED_MESSAGE)
        String username,

        @NotBlank(message = CredentialPolicy.PASSWORD_REQUIRED_MESSAGE)
        String password) {

    public LoginRequest {
        username = username == null ? null : username.trim();
    }
}
