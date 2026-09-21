package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidUserDataException;
import lombok.Getter;

/**
 * Persona registrada y titular de las dos credenciales: la contraseña y la clave de API.
 *
 * <p>No tiene constructores públicos con estado arbitrario: se crea solo a través de sus
 * fábricas, que son las que aplican las invariantes. Tampoco tiene setters; el saldo y los dos
 * hashes solo cambian a través de métodos de esta clase.</p>
 */
@Getter
public class AppUser {

    private final Long id;
    private final String username;
    private final String email;
    private final Role role;
    private String passwordHash;
    private String apiKeyHash;
    private BigDecimal balance;

    private AppUser(Long id, String username, String email, String passwordHash, String apiKeyHash,
                    Role role, BigDecimal balance) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.apiKeyHash = apiKeyHash;
        this.role = role;
        this.balance = balance;
    }

    /**
     * Crea la cuenta del registro público: rol {@code USER}, el saldo inicial configurado y una
     * clave de API recién emitida (FR-010, FR-012 y FR-013).
     */
    public static RegisteredUser register(String username, String email, String rawPassword,
                                          BigDecimal initialBalance, PasswordHasher hasher) {
        validateCredentials(username, email, rawPassword);
        AppUser user = new AppUser(null, username, email, hasher.hash(rawPassword), null, Role.USER,
                normalizedBalance(initialBalance));
        ApiKey apiKey = user.issueApiKey();
        return new RegisteredUser(user, apiKey);
    }

    /**
     * Reconstruye el estado leído de la base. No valida el formato porque ese estado ya se
     * validó al crearse. La usa únicamente {@code AppUserMapper.toDomain}.
     */
    public static AppUser reconstitute(Long id, String username, String email, String passwordHash,
                                       String apiKeyHash, Role role, BigDecimal balance) {
        return new AppUser(id, username, email, passwordHash, apiKeyHash, role, balance);
    }

    /**
     * Emite una clave nueva y devuelve su valor en claro, que existe solo en el objeto devuelto.
     * La clave anterior deja de valer en el acto, porque se pisa su hash (FR-032 a FR-034).
     */
    public ApiKey issueApiKey() {
        ApiKey apiKey = ApiKey.generate();
        this.apiKeyHash = apiKey.hash();
        return apiKey;
    }

    /**
     * Verifica la contraseña contra el hash guardado. El mensaje de la excepción es el mismo que
     * usa el servicio para un usuario inexistente, así el rechazo no revela cuál de los dos falló
     * (FR-017 y SC-007).
     */
    public void verifyPassword(String rawPassword, PasswordHasher hasher) {
        if (!hasher.matches(rawPassword, passwordHash)) {
            throw new InvalidCredentialsException();
        }
    }

    /** No incluye ninguno de los dos hashes. */
    @Override
    public String toString() {
        return "AppUser{id=%s, username='%s', email='%s', role=%s, balance=%s}"
                .formatted(id, username, email, role, balance);
    }

    /**
     * Invariantes de formato de las credenciales, compartidas por todas las fábricas. El mensaje
     * es el de la regla incumplida y nunca repite el valor recibido.
     */
    private static void validateCredentials(String username, String email, String rawPassword) {
        validateUsername(username);
        validateEmail(email);
        validatePassword(rawPassword);
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidUserDataException(CredentialPolicy.USERNAME_REQUIRED_MESSAGE);
        }
        if (username.length() < CredentialPolicy.USERNAME_MIN_LENGTH
                || username.length() > CredentialPolicy.USERNAME_MAX_LENGTH) {
            throw new InvalidUserDataException(CredentialPolicy.USERNAME_LENGTH_MESSAGE);
        }
        if (!CredentialPolicy.isValidUsername(username)) {
            throw new InvalidUserDataException(CredentialPolicy.USERNAME_FORMAT_MESSAGE);
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidUserDataException(CredentialPolicy.EMAIL_REQUIRED_MESSAGE);
        }
        if (email.length() > CredentialPolicy.EMAIL_MAX_LENGTH) {
            throw new InvalidUserDataException(CredentialPolicy.EMAIL_LENGTH_MESSAGE);
        }
        if (!CredentialPolicy.isValidEmail(email)) {
            throw new InvalidUserDataException(CredentialPolicy.EMAIL_FORMAT_MESSAGE);
        }
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidUserDataException(CredentialPolicy.PASSWORD_REQUIRED_MESSAGE);
        }
        if (!CredentialPolicy.isValidPassword(rawPassword)) {
            throw new InvalidUserDataException(CredentialPolicy.PASSWORD_LENGTH_MESSAGE);
        }
    }

    /**
     * El redondeo no llega a aplicarse: la configuración ya acota el saldo inicial a dos
     * decimales (`AuthProperties`). Acá solo se fija la escala para que todos los saldos se
     * comparen y se persistan igual.
     */
    private static BigDecimal normalizedBalance(BigDecimal balance) {
        return balance.setScale(CredentialPolicy.BALANCE_SCALE, RoundingMode.HALF_UP);
    }
}
