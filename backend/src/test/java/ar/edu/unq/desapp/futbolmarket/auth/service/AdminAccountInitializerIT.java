package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.ApiKey;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.sql.interfaces.AppUserSQLDAO;

/**
 * Alta del administrador contra la base real.
 *
 * <p>Usa una H2 en memoria propia: con {@code create-drop} sobre la {@code testdb} compartida,
 * este contexto borraría las tablas de los contextos cacheados y dejaría un administrador visible
 * para otros tests (research D17). Las credenciales se generan al azar en cada corrida, así
 * ninguna queda escrita en el repositorio.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAccountInitializerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final int BALANCE_SCALE = 2;

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admin-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private AdminAccountInitializer initializer;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppUserSQLDAO appUserSQLDAO;

    private final ApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void trasElArranqueExisteUnUnicoAdministradorConSaldoCeroYSinClave() {
        AppUser admin = appUserRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        assertThat(appUserSQLDAO.count()).isOne();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(admin.getBalance().scale()).isEqualTo(BALANCE_SCALE);
        assertThat(admin.getApiKeyHash()).isNull();
    }

    /** SC-011: el alta es idempotente, aunque se ejecute varias veces. */
    @Test
    void ejecutarElAltaOtraVezNoCambiaLaContrasena() {
        String hashBefore = appUserRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getPasswordHash();

        initializer.run(args);
        initializer.run(args);

        assertThat(appUserRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getPasswordHash())
                .isEqualTo(hashBefore);
        assertThat(appUserSQLDAO.count()).isOne();
    }

    /**
     * Escenario 3 de la HU7. Es el único test que modifica al administrador, así que descarta el
     * contexto al terminar: el siguiente arranca con una base nueva y el administrador recién
     * creado, sin importar el orden en que corran los tests.
     */
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void ejecutarElAltaOtraVezNoCambiaLaClaveQueSeEmitioElAdministrador() {
        AppUser admin = appUserRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        ApiKey issued = admin.issueApiKey();
        appUserRepository.save(admin);

        initializer.run(args);

        assertThat(appUserRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getApiKeyHash())
                .isEqualTo(issued.hash());
    }
}
