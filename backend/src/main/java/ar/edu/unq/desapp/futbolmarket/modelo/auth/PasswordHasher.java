package ar.edu.unq.desapp.futbolmarket.modelo.auth;

/**
 * Puerto del modelo para hashear y verificar contraseñas. Lo implementa
 * {@code security/BCryptPasswordHasher}, así el modelo no conoce a Spring Security.
 */
public interface PasswordHasher {

    String hash(String plainText);

    boolean matches(String plainText, String hash);
}
