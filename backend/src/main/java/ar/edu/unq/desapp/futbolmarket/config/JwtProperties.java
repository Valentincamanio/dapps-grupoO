package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades del token de sesión. El secreto nunca se imprime: no está en la configuración
 * base y en producción viaja solo por variable de entorno.
 */
@ConfigurationProperties("futbolmarket.security.jwt")
@Validated
public record JwtProperties(

        @NotBlank
        String secret,

        @NotNull
        Duration expiration) {

    private static final String MASKED_VALUE = "****";

    public JwtProperties {
        if (expiration != null && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException("La duración del token de sesión tiene que ser positiva.");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=%s, expiration=%s]".formatted(MASKED_VALUE, expiration);
    }
}
