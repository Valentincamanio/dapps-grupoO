package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.CredentialPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del cambio de contraseña. Ninguno de los dos campos se recorta: los espacios son parte
 * de la contraseña.
 *
 * <p>La nueva lleva las mismas reglas que en el registro (FR-029). La actual solo se exige: si no
 * coincide, lo decide el modelo, que es quien conoce el hash.</p>
 */
public record ChangePasswordRequest(

        @NotBlank(message = CredentialPolicy.PASSWORD_REQUIRED_MESSAGE)
        String currentPassword,

        @NotBlank(message = CredentialPolicy.PASSWORD_REQUIRED_MESSAGE)
        @Size(min = CredentialPolicy.PASSWORD_MIN_LENGTH, max = CredentialPolicy.PASSWORD_MAX_LENGTH,
                message = CredentialPolicy.PASSWORD_LENGTH_MESSAGE)
        String newPassword) {
}
