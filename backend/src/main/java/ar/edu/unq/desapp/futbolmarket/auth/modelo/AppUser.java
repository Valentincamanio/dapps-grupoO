package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import java.math.BigDecimal;

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
     * Reconstruye el estado leído de la base. No valida el formato porque ese estado ya se
     * validó al crearse. La usa únicamente {@code AppUserMapper.toDomain}.
     */
    public static AppUser reconstitute(Long id, String username, String email, String passwordHash,
                                       String apiKeyHash, Role role, BigDecimal balance) {
        return new AppUser(id, username, email, passwordHash, apiKeyHash, role, balance);
    }

    /** No incluye ninguno de los dos hashes. */
    @Override
    public String toString() {
        return "AppUser{id=%s, username='%s', email='%s', role=%s, balance=%s}"
                .formatted(id, username, email, role, balance);
    }
}
