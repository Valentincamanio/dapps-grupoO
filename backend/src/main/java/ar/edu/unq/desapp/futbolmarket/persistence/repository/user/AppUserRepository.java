package ar.edu.unq.desapp.futbolmarket.persistence.repository.user;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.user.AppUserMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketLocks;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
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
     * La cuenta con su fila bloqueada hasta que termine la transacción de quien la pide, que tiene
     * que existir (research D13 de la 008). Si otro pedido la tiene bloqueada más que el tiempo de
     * espera de la base, lanza {@code MarketBusyException}.
     */
    public Optional<AppUser> lockById(Long id) {
        return MarketLocks.orBusy(() -> appUserSQLDAO.findWithLockById(id)).map(appUserMapper::toDomain);
    }

    /**
     * Bloquea las cuentas de a una y de menor a mayor id: dos pedidos que comparten cuentas las
     * piden en el mismo orden y no se traban entre sí (research D13). Repetir un id no lo bloquea
     * dos veces, y las cuentas que no existen no figuran.
     *
     * @return las cuentas por id, en el orden en que se bloquearon
     */
    public Map<Long, AppUser> lockAllInOrder(Collection<Long> ids) {
        Map<Long, AppUser> locked = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(ids)) {
            lockById(id).ifPresent(user -> locked.put(id, user));
        }
        return locked;
    }

    /**
     * El superusuario del mercado: la primera cuenta {@code ADMIN}, la del alta de arranque. No se
     * busca por el nombre configurado porque, si la configuración cambia, la cuenta original sigue
     * siendo la dueña de las órdenes iniciales (research D7 de la 008).
     */
    public Optional<AppUser> findSuperuser() {
        return appUserSQLDAO.findFirstByRoleOrderByIdAsc(Role.ADMIN.name()).map(appUserMapper::toDomain);
    }

    /**
     * El id del superusuario sin cargar su cuenta, para bloquearla con {@link #lockById} antes de
     * leerla: una cuenta que ya está en el contexto de persistencia no se refresca al bloquearla
     * (research D13 de la 008).
     */
    public Optional<Long> findSuperuserId() {
        return Optional.ofNullable(appUserSQLDAO.findFirstIdByRole(Role.ADMIN.name()));
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
