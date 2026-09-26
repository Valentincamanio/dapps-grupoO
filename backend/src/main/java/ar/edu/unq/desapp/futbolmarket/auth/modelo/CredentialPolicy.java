package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Única fuente de verdad de las reglas de formato de las credenciales.
 *
 * <p>Las constantes las comparten los DTO de request, en sus anotaciones de Bean Validation, y
 * las fábricas de {@link AppUser}, que deciden con los predicados qué excepción lanzar. Al
 * compartir la fuente, el DTO y el modelo no pueden divergir.</p>
 *
 * <p>Los mensajes son literales y no se arman con {@code formatted}, porque las anotaciones de
 * Bean Validation solo aceptan constantes de tiempo de compilación.</p>
 */
public final class CredentialPolicy {

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 30;
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_]+$";
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 72;
    public static final int PASSWORD_MAX_BYTES = 72;
    public static final int BALANCE_SCALE = 2;

    public static final String USERNAME_REQUIRED_MESSAGE = "El nombre de usuario es obligatorio.";
    public static final String USERNAME_LENGTH_MESSAGE = "El nombre de usuario debe tener entre 3 y 30 caracteres.";
    public static final String USERNAME_FORMAT_MESSAGE = "El nombre de usuario solo puede tener letras, números y guiones bajos.";
    public static final String EMAIL_REQUIRED_MESSAGE = "El correo electrónico es obligatorio.";
    public static final String EMAIL_LENGTH_MESSAGE = "El correo electrónico debe tener a lo sumo 254 caracteres.";
    public static final String EMAIL_FORMAT_MESSAGE = "El correo electrónico no tiene un formato válido.";
    public static final String PASSWORD_REQUIRED_MESSAGE = "La contraseña es obligatoria.";
    public static final String PASSWORD_LENGTH_MESSAGE = "La contraseña debe tener entre 8 y 72 caracteres.";

    private static final Pattern USERNAME_REGEX = Pattern.compile(USERNAME_PATTERN);
    private static final Pattern EMAIL_REGEX = Pattern.compile(EMAIL_PATTERN);

    private CredentialPolicy() {
        // Clase de constantes y predicados puros: no se instancia.
    }

    public static boolean isValidUsername(String username) {
        return username != null
                && username.length() >= USERNAME_MIN_LENGTH
                && username.length() <= USERNAME_MAX_LENGTH
                && USERNAME_REGEX.matcher(username).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null
                && email.length() <= EMAIL_MAX_LENGTH
                && EMAIL_REGEX.matcher(email).matches();
    }

    /**
     * El mínimo se cuenta en caracteres y el máximo en bytes UTF-8, porque el límite de 72 bytes
     * es el de BCrypt y una contraseña con caracteres multibyte lo alcanza antes de los 72
     * caracteres (research D5).
     */
    public static boolean isValidPassword(String password) {
        return password != null
                && password.length() >= PASSWORD_MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }
}
