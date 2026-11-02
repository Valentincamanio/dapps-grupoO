package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;
import jakarta.persistence.LockModeType;

/**
 * Acceso a la tabla {@code app_user}. Derived queries y JPQL: no hay SQL nativo (principio VI).
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

    /**
     * El menor id con ese rol, sin cargar la cuenta; nulo si no hay ninguna. Así la cuenta se puede
     * bloquear antes de leerla (research D13 de la 008).
     */
    @Query("select min(u.id) from AppUserSQL u where u.role = :role")
    Long findFirstIdByRole(@Param("role") String role);
}
