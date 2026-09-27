package ar.edu.unq.desapp.futbolmarket.auth.modelo;

/**
 * Doble de test determinístico del puerto {@link PasswordHasher}: evita el costo de BCrypt y
 * hace predecible el hash esperado.
 */
public class FakePasswordHasher implements PasswordHasher {

    private static final String PREFIX = "hashed:";

    @Override
    public String hash(String plainText) {
        return PREFIX + plainText;
    }

    @Override
    public boolean matches(String plainText, String hashedValue) {
        return hash(plainText).equals(hashedValue);
    }
}
