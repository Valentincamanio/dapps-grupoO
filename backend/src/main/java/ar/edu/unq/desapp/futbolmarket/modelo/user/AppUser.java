package ar.edu.unq.desapp.futbolmarket.modelo.user;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.CredentialPolicy;
import ar.edu.unq.desapp.futbolmarket.modelo.auth.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.TradingNotAllowedException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.AccountAccessDeniedException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.DateBeforeRegistrationException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.InvalidPasswordChangeException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.InvalidUserDataException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Persona registrada y titular de las dos credenciales: la contraseña y la clave de API.
 *
 * <p>No tiene constructores públicos con estado arbitrario: se crea solo a través de sus
 * fábricas, que son las que aplican las invariantes. Tampoco tiene setters; los saldos y los dos
 * hashes solo cambian a través de métodos de esta clase.</p>
 *
 * <p>{@code balance} es el saldo total; {@code reserved}, la parte reservada para compras
 * pendientes, y el libre es la diferencia (research D5 de la 008). Reservar y liberar no mueven el
 * total: solo lo mueven pagar y cobrar, así los créditos de todas las cuentas se conservan.</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUser {

    private static final String WRONG_CURRENT_PASSWORD_MESSAGE = "La contraseña actual es incorrecta.";
    private static final String SAME_PASSWORD_MESSAGE = "La nueva contraseña debe ser distinta de la actual.";
    private static final String INVALID_NEW_PASSWORD_MESSAGE = "La nueva contraseña no cumple las reglas de formato.";
    private static final BigDecimal NO_RESERVATION = BigDecimal.ZERO.setScale(CredentialPolicy.BALANCE_SCALE);

    private final Long id;
    private final String username;
    private final String email;
    private final Role role;
    private String passwordHash;
    private String apiKeyHash;
    private BigDecimal balance;
    private BigDecimal reserved;
    /** Nulo en las cuentas creadas antes de la 008 (research D6). */
    private final Instant registeredAt;

    /**
     * Crea la cuenta del registro público: rol {@code USER}, el saldo inicial configurado y una
     * clave de API recién emitida (FR-010, FR-012 y FR-013).
     */
    public static RegisteredUser register(String username, String email, String rawPassword,
                                          BigDecimal initialBalance, PasswordHasher hasher) {
        return register(username, email, rawPassword, initialBalance, hasher, null);
    }

    /**
     * Como {@link #register(String, String, String, BigDecimal, PasswordHasher)}, con el instante
     * del registro (research D6 de la 008).
     */
    public static RegisteredUser register(String username, String email, String rawPassword,
                                          BigDecimal initialBalance, PasswordHasher hasher, Instant registeredAt) {
        AppUser user = newAccount(username, email, rawPassword, Role.USER, initialBalance, hasher, registeredAt);
        ApiKey apiKey = user.issueApiKey();
        return new RegisteredUser(user, apiKey);
    }

    /**
     * Crea la cuenta del alta de arranque: rol {@code ADMIN}, saldo cero y sin clave de API. La
     * clave la emite el propio administrador después, desde su cuenta (FR-040 a FR-042).
     */
    public static AppUser createAdmin(String username, String email, String rawPassword,
                                      PasswordHasher hasher) {
        return createAdmin(username, email, rawPassword, hasher, null);
    }

    /**
     * Como {@link #createAdmin(String, String, String, PasswordHasher)}, con el instante del alta.
     */
    public static AppUser createAdmin(String username, String email, String rawPassword,
                                      PasswordHasher hasher, Instant registeredAt) {
        return newAccount(username, email, rawPassword, Role.ADMIN, BigDecimal.ZERO, hasher, registeredAt);
    }

    /**
     * Reconstruye el estado leído de la base. No valida el formato porque ese estado ya se
     * validó al crearse. La usa únicamente {@code AppUserMapper.toDomain}.
     */
    public static AppUser reconstitute(Long id, String username, String email, String passwordHash,
                                       String apiKeyHash, Role role, BigDecimal balance) {
        return reconstitute(id, username, email, passwordHash, apiKeyHash, role, balance, NO_RESERVATION, null);
    }

    /**
     * Como la de siete argumentos, con el saldo reservado y el instante del registro.
     */
    public static AppUser reconstitute(Long id, String username, String email, String passwordHash,
                                       String apiKeyHash, Role role, BigDecimal balance, BigDecimal reserved,
                                       Instant registeredAt) {
        return new AppUser(id, username, email, role, passwordHash, apiKeyHash, balance, reserved, registeredAt);
    }

    public BigDecimal availableBalance() {
        return balance.subtract(reserved);
    }

    /**
     * Pasa créditos del libre al reservado para una compra pendiente (FR-015 y FR-019).
     */
    public void reserve(BigDecimal amount) {
        requireNotNegative(amount);
        if (amount.compareTo(availableBalance()) > 0) {
            throw new InsufficientCreditsException(amount, availableBalance());
        }
        reserved = reserved.add(amount);
    }

    /**
     * Devuelve al libre créditos reservados, al cancelar o al ajustar la reserva de una compra.
     */
    public void release(BigDecimal amount) {
        requireWithinReservation(amount);
        reserved = reserved.subtract(amount);
    }

    /**
     * Paga una operación con lo reservado: baja el total y el reservado, y el libre no cambia.
     */
    public void payFromReserve(BigDecimal amount) {
        requireWithinReservation(amount);
        balance = balance.subtract(amount);
        reserved = reserved.subtract(amount);
    }

    /**
     * Cobra una venta.
     */
    public void receive(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new MarketInvariantException("Lo que se cobra tiene que ser mayor que 0: " + amount + ".");
        }
        balance = balance.add(amount);
    }

    /**
     * Solo los usuarios comunes operan; el superusuario solo vende por sus órdenes iniciales
     * (FR-010).
     */
    public void requireCanTrade() {
        if (role != Role.USER) {
            throw new TradingNotAllowedException();
        }
    }

    /**
     * El portfolio, el historial y las órdenes de una cuenta los ven su dueño y el administrador
     * (FR-042 y FR-046).
     */
    public void requireCanAccessAccountOf(long userId) {
        if (role != Role.ADMIN && !Objects.equals(id, userId)) {
            throw new AccountAccessDeniedException();
        }
    }

    /**
     * Rechaza un día anterior al del registro, en hora de Argentina (FR-041). Una cuenta anterior
     * a la 008 no tiene fecha de registro, ni operaciones de antes, así que para ella no hay
     * límite.
     */
    public void requireRegisteredBy(LocalDate day) {
        if (registeredAt == null) {
            return;
        }
        LocalDate registrationDay = LocalDate.ofInstant(registeredAt, QuoteWeek.ZONE);
        if (day.isBefore(registrationDay)) {
            throw new DateBeforeRegistrationException(day, registrationDay);
        }
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

    /**
     * Cambia la contraseña después de tres controles, en este orden: que la actual coincida, que
     * la nueva sea distinta y que la nueva cumpla el formato. El orden importa: si la actual es
     * incorrecta, eso es lo que se informa aunque la nueva también sea inválida. Si algún control
     * falla, el hash no se toca (FR-027 a FR-031).
     *
     * <p>Los tokens ya emitidos siguen valiendo: el token no depende de la contraseña (FR-035).</p>
     */
    public void changePassword(String currentPassword, String newPassword, PasswordHasher hasher) {
        if (!hasher.matches(currentPassword, passwordHash)) {
            throw new InvalidPasswordChangeException(WRONG_CURRENT_PASSWORD_MESSAGE);
        }
        if (Objects.equals(newPassword, currentPassword)) {
            throw new InvalidPasswordChangeException(SAME_PASSWORD_MESSAGE);
        }
        if (!CredentialPolicy.isValidPassword(newPassword)) {
            throw new InvalidPasswordChangeException(INVALID_NEW_PASSWORD_MESSAGE);
        }
        this.passwordHash = hasher.hash(newPassword);
    }

    /** No incluye ninguno de los dos hashes. */
    @Override
    public String toString() {
        return "AppUser{id=%s, username='%s', email='%s', role=%s, balance=%s, reserved=%s}"
                .formatted(id, username, email, role, balance, reserved);
    }

    /**
     * Lo que comparten las dos fábricas de cuentas nuevas: validar las credenciales, hashear la
     * contraseña y fijar la escala del saldo. Cada fábrica decide el rol, el saldo y la clave.
     */
    private static AppUser newAccount(String username, String email, String rawPassword, Role role,
                                      BigDecimal balance, PasswordHasher hasher, Instant registeredAt) {
        validateCredentials(username, email, rawPassword);
        return new AppUser(null, username, email, role, hasher.hash(rawPassword), null,
                normalizedBalance(balance), NO_RESERVATION, registeredAt);
    }

    private static void requireNotNegative(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new MarketInvariantException("Un monto de créditos no puede ser negativo: " + amount + ".");
        }
    }

    private void requireWithinReservation(BigDecimal amount) {
        requireNotNegative(amount);
        if (amount.compareTo(reserved) > 0) {
            throw new MarketInvariantException("No se pueden mover " + amount + " créditos con " + reserved
                    + " reservados.");
        }
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
