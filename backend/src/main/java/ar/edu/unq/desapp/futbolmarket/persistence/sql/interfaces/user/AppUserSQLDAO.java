package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;
import jakarta.persistence.LockModeType;

/**
 * Acceso a la tabla {@code app_user}. Solo derived queries: no hay SQL nativo (principio VI).
 */
public interface AppUserSQLDAO extends JpaRepository<AppUserSQL, Long> {

    Optional<AppUserSQL> findByUsernameIgnoreCase(String username);

    Optional<AppUserSQL> findByApiKeyHash(String apiKeyHash);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * La cuenta con su fila bloqueada hasta el final de la transacción ({@code SELECT ... FOR
     * UPDATE}), para que dos pedidos del mercado no la cambien a la vez (research D13 de la 008).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUserSQL> findWithLockById(Long id);

    Optional<AppUserSQL> findFirstByRoleOrderByIdAsc(String role);
}
