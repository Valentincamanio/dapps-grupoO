package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.CredentialPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del registro público.
 *
 * <p>Ignora las propiedades desconocidas a propósito: si el cliente manda un {@code role}, se
 * descarta y la cuenta se crea igual con rol de usuario común. El comportamiento es explícito y
 * no depende de la configuración global de Jackson (research D11).</p>
 *
 * <p>El username y el correo se recortan antes de validar y de comparar. La contraseña nunca se
 * recorta: los espacios son parte de la contraseña.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(

        @NotBlank(message = CredentialPolicy.USERNAME_REQUIRED_MESSAGE)
        @Size(min = CredentialPolicy.USERNAME_MIN_LENGTH, max = CredentialPolicy.USERNAME_MAX_LENGTH,
                message = CredentialPolicy.USERNAME_LENGTH_MESSAGE)
        @Pattern(regexp = CredentialPolicy.USERNAME_PATTERN, message = CredentialPolicy.USERNAME_FORMAT_MESSAGE)
        String username,

        @NotBlank(message = CredentialPolicy.EMAIL_REQUIRED_MESSAGE)
        @Email(regexp = CredentialPolicy.EMAIL_PATTERN, message = CredentialPolicy.EMAIL_FORMAT_MESSAGE)
        @Size(max = CredentialPolicy.EMAIL_MAX_LENGTH, message = CredentialPolicy.EMAIL_LENGTH_MESSAGE)
        String email,

        @NotBlank(message = CredentialPolicy.PASSWORD_REQUIRED_MESSAGE)
        @Size(min = CredentialPolicy.PASSWORD_MIN_LENGTH, max = CredentialPolicy.PASSWORD_MAX_LENGTH,
                message = CredentialPolicy.PASSWORD_LENGTH_MESSAGE)
        String password) {

    public RegisterRequest {
        username = trimmed(username);
        email = trimmed(email);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
