package ar.edu.unq.desapp.futbolmarket.observability;

import java.util.Locale;

/**
 * Cómo terminó un intento de inicio de sesión: la etiqueta {@code result} de
 * {@code futbolmarket.auth.logins}. Un usuario inexistente y una contraseña incorrecta son el mismo
 * {@code FAILURE}.
 */
public enum LoginResult {
    SUCCESS,
    FAILURE;

    /**
     * El valor de la etiqueta: el nombre en minúsculas.
     */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
