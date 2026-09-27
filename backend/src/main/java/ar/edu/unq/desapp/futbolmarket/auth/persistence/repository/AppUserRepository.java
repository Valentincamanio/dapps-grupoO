package ar.edu.unq.desapp.futbolmarket.auth.persistence.repository;

import java.util.Locale;
import java.util.Optional;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.mapper.AppUserMapper;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.interfaces.AppUserSQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Traduce en la frontera con la persistencia: recibe y devuelve {@link AppUser}, y delega en
 * {@link AppUserMapper} la conversión desde y hacia las clases {@code *SQL}.
 *
 * <p>Esa traducción es la que mantiene al servicio ignorante de JPA: no ve el DAO, ni las clases
 * {@code *SQL}, ni el mapper. Las violaciones de integridad también se traducen acá, a
 * excepciones del modelo, así tampoco se filtra hacia arriba una excepción de persistencia.</p>
 */
@Repository
@RequiredArgsConstructor
public class AppUserRepository {

    private static final String USERNAME_INDEX = "ux_app_user_username";
    private static final String EMAIL_INDEX = "ux_app_user_email";

    private final AppUserSQLDAO appUserSQLDAO;
    private final AppUserMapper appUserMapper;

    /**
     * Persiste con {@code saveAndFlush} para que una violación de índice único aparezca dentro
     * de este método y no al cerrar la transacción. Después de un flush fallido la sesión de
     * Hibernate queda inutilizable, así que no se vuelve a consultar la base (research D10).
     */
    public AppUser save(AppUser user) {
        try {
            return appUserMapper.toDomain(appUserSQLDAO.saveAndFlush(appUserMapper.toSQL(user)));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    public Optional<AppUser> findById(Long id) {
        return appUserSQLDAO.findById(id).map(appUserMapper::toDomain);
    }

    public Optional<AppUser> findByUsername(String username) {
        return appUserSQLDAO.findByUsernameIgnoreCase(username).map(appUserMapper::toDomain);
    }

    public Optional<AppUser> findByApiKeyHash(String apiKeyHash) {
        return appUserSQLDAO.findByApiKeyHash(apiKeyHash).map(appUserMapper::toDomain);
    }

    public boolean existsByUsername(String username) {
        return appUserSQLDAO.existsByUsernameIgnoreCase(username);
    }

    public boolean existsByEmail(String email) {
        return appUserSQLDAO.existsByEmailIgnoreCase(email);
    }

    /**
     * Reconoce el índice por su nombre en la causa más específica, sin distinguir mayúsculas
     * porque H2 informa los nombres en mayúsculas. Cualquier otro caso se relanza sin tocarlo.
     */
    private RuntimeException translate(DataIntegrityViolationException e) {
        String cause = mostSpecificCauseMessage(e);
        if (cause.contains(USERNAME_INDEX)) {
            return new DuplicateUsernameException();
        }
        if (cause.contains(EMAIL_INDEX)) {
            return new DuplicateEmailException();
        }
        return e;
    }

    private String mostSpecificCauseMessage(DataIntegrityViolationException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }
}
