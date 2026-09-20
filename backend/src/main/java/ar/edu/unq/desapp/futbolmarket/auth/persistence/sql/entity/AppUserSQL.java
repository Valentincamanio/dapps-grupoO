package ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.entity;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.CredentialPolicy;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contraparte de persistencia de {@code AppUser}. Es la única clase de la feature con
 * anotaciones de JPA.
 *
 * <p>El nombre de la tabla es explícito porque {@code user} es palabra reservada en PostgreSQL,
 * y los tres índices únicos se declaran con nombre fijo para poder traducir su violación a una
 * excepción de dominio en el repository (research D10).</p>
 */
@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "ux_app_user_username", columnList = "username", unique = true),
        @Index(name = "ux_app_user_email", columnList = "email", unique = true),
        @Index(name = "ux_app_user_api_key_hash", columnList = "api_key_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class AppUserSQL {

    private static final int PASSWORD_HASH_LENGTH = 60;
    private static final int API_KEY_HASH_LENGTH = 64;
    private static final int ROLE_LENGTH = 20;
    private static final int BALANCE_PRECISION = 19;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = CredentialPolicy.USERNAME_MAX_LENGTH)
    private String username;

    @Column(nullable = false, length = CredentialPolicy.EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false, length = PASSWORD_HASH_LENGTH)
    private String passwordHash;

    @Column(name = "api_key_hash", length = API_KEY_HASH_LENGTH)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = ROLE_LENGTH)
    private Role role;

    @Column(nullable = false, precision = BALANCE_PRECISION, scale = CredentialPolicy.BALANCE_SCALE)
    private BigDecimal balance;
}
