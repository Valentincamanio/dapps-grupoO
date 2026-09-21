package ar.edu.unq.desapp.futbolmarket.auth.persistence.mapper;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.entity.AppUserSQL;

/**
 * Traduce campo a campo entre {@link AppUser} y {@link AppUserSQL}, en las dos direcciones y sin
 * lógica de negocio. El rol viaja como su nombre, nunca como ordinal.
 */
@Component
public class AppUserMapper {

    public AppUser toDomain(AppUserSQL entity) {
        return AppUser.reconstitute(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getApiKeyHash(),
                Role.valueOf(entity.getRole()),
                entity.getBalance());
    }

    /** Si el id viene en {@code null}, JPA inserta; si no, actualiza. */
    public AppUserSQL toSQL(AppUser user) {
        AppUserSQL entity = new AppUserSQL();
        entity.setId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setApiKeyHash(user.getApiKeyHash());
        entity.setRole(user.getRole().name());
        entity.setBalance(user.getBalance());
        return entity;
    }
}
