package ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.interfaces;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.entity.AppUserSQL;

/**
 * Acceso a la tabla {@code app_user}. Solo derived queries: no hay SQL nativo (principio VI).
 */
public interface AppUserSQLDAO extends JpaRepository<AppUserSQL, Long> {

    Optional<AppUserSQL> findByUsernameIgnoreCase(String username);

    Optional<AppUserSQL> findByApiKeyHash(String apiKeyHash);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
}
