package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Clave de API como objeto de valor. Solo usa la JDK, así que no hace falta un puerto y el
 * modelo se mantiene sin dependencias de infraestructura.
 *
 * <p>Se persiste el hash SHA-256 y nunca el valor en claro. SHA-256 es determinístico, y por eso
 * permite buscar al usuario por igualdad; es seguro acá porque la clave tiene 256 bits de
 * entropía (research D4).</p>
 */
public record ApiKey(String value) {

    private static final int KEY_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String MASKED_VALUE = "****";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Genera una clave nueva de 32 bytes en Base64URL sin padding: 43 caracteres. */
    public static ApiKey generate() {
        byte[] bytes = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return new ApiKey(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** SHA-256 en hexadecimal en minúsculas: 64 caracteres. */
    public static String hashOf(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El algoritmo " + HASH_ALGORITHM + " tiene que estar disponible en la JVM.", e);
        }
    }

    public String hash() {
        return hashOf(value);
    }

    @Override
    public String toString() {
        return "ApiKey[value=" + MASKED_VALUE + "]";
    }
}
