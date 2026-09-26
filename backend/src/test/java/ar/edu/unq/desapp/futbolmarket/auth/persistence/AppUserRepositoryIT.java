package ar.edu.unq.desapp.futbolmarket.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.ApiKey;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.mapper.AppUserMapper;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;

@DataJpaTest
@ActiveProfiles("test")
@Import({AppUserRepository.class, AppUserMapper.class})
class AppUserRepositoryIT {

    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int BALANCE_SCALE = 2;
    private static final String VARCHAR_TYPE = "CHARACTER VARYING";
    private static final int ROLE_LENGTH = 20;

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void guardaYRecuperaLosSieteCampos() {
        String apiKeyHash = ApiKey.generate().hash();

        AppUser saved = repository.save(user("lionel10", "lionel@correo.com", apiKeyHash));
        AppUser found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getUsername()).isEqualTo("lionel10");
        assertThat(found.getEmail()).isEqualTo("lionel@correo.com");
        assertThat(found.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(found.getApiKeyHash()).isEqualTo(apiKeyHash);
        assertThat(found.getRole()).isEqualTo(Role.USER);
        assertThat(found.getBalance()).isEqualByComparingTo(INITIAL_BALANCE);
        assertThat(found.getBalance().scale()).isEqualTo(BALANCE_SCALE);
    }

    /**
     * Se consulta la columna directamente porque es la única forma de ver cómo quedó guardado el
     * rol: desde el modelo, un mapeo por ordinal se vería igual que uno por texto.
     */
    @Test
    void persisteElRolComoTexto() {
        AppUser saved = repository.save(user("angel11", "angel@correo.com", null));

        Object storedRole = entityManager.getEntityManager()
                .createNativeQuery("SELECT role FROM app_user WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(storedRole).isEqualTo(Role.USER.name());
    }

    /**
     * El test anterior no alcanza para ver el tipo de la columna: H2 devuelve como texto también
     * los valores de un {@code ENUM} nativo. Por eso se mira el esquema.
     */
    @Test
    void laColumnaDelRolEsUnVarcharDeVeinteCaracteres() {
        Object[] column = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'APP_USER' AND COLUMN_NAME = 'ROLE'")
                .getSingleResult();

        assertThat(column[0]).isEqualTo(VARCHAR_TYPE);
        assertThat(((Number) column[1]).intValue()).isEqualTo(ROLE_LENGTH);
    }

    @Test
    void findByUsernameIgnoraMayusculas() {
        repository.save(user("Lionel10", "lionel@correo.com", null));

        assertThat(repository.findByUsername("lionel10")).isPresent();
        assertThat(repository.existsByUsername("LIONEL10")).isTrue();
    }

    @Test
    void existsByEmailIgnoraMayusculas() {
        repository.save(user("lionel10", "Lionel@Correo.com", null));

        assertThat(repository.existsByEmail("lionel@correo.com")).isTrue();
    }

    @Test
    void lanzaDuplicateUsernameCuandoElNombreDeUsuarioYaExiste() {
        repository.save(user("lionel10", "lionel@correo.com", null));

        assertThatThrownBy(() -> repository.save(user("lionel10", "otro@correo.com", null)))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessage("El nombre de usuario ya está registrado.");
    }

    @Test
    void lanzaDuplicateEmailCuandoElCorreoYaExiste() {
        repository.save(user("lionel10", "lionel@correo.com", null));

        assertThatThrownBy(() -> repository.save(user("otro10", "lionel@correo.com", null)))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("El correo electrónico ya está registrado.");
    }

    @Test
    void relanzaLaViolacionDeIntegridadCuandoSeRepiteElHashDeLaClaveDeApi() {
        String apiKeyHash = ApiKey.generate().hash();
        repository.save(user("lionel10", "lionel@correo.com", apiKeyHash));

        assertThatThrownBy(() -> repository.save(user("otro10", "otro@correo.com", apiKeyHash)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void admiteVariosUsuariosSinClaveDeApi() {
        repository.save(user("lionel10", "lionel@correo.com", null));
        AppUser segundo = repository.save(user("angel11", "angel@correo.com", null));

        assertThat(segundo.getId()).isNotNull();
        assertThat(segundo.getApiKeyHash()).isNull();
    }

    private AppUser user(String username, String email, String apiKeyHash) {
        return AppUser.reconstitute(null, username, email, PASSWORD_HASH, apiKeyHash, Role.USER, INITIAL_BALANCE);
    }
}
