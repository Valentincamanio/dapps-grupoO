package ar.edu.unq.desapp.futbolmarket.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Propiedades del registro y del alta del administrador.
 *
 * <p>Los datos del administrador no llevan validaciones: si faltan o son inválidos, la
 * aplicación arranca igual y el inicializador omite el alta con una advertencia (FR-038). Por
 * eso {@code admin} puede llegar en {@code null}.</p>
 */
@ConfigurationProperties("futbolmarket.auth")
@Validated
public record AuthProperties(

        @NotNull
        @PositiveOrZero
        @Digits(integer = 17, fraction = 2)
        BigDecimal initialBalance,

        Admin admin) {

    /**
     * Credenciales del administrador. Su {@code toString} enmascara la contraseña, y como el
     * {@code toString} de {@link AuthProperties} delega en este, la contraseña no aparece
     * tampoco al imprimir las propiedades completas.
     */
    public record Admin(String username, String email, String password) {

        private static final String MASKED_VALUE = "****";

        @Override
        public String toString() {
            return "Admin[username=%s, email=%s, password=%s]".formatted(username, email, MASKED_VALUE);
        }
    }
}
