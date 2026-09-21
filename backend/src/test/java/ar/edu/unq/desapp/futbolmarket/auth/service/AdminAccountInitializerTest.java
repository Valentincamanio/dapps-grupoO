package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.CredentialPolicy;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;

/**
 * Como el {@code FakePasswordHasher} arma el hash con la contraseña en claro adentro, verificar que
 * la salida no contenga la contraseña también descarta que se haya registrado su hash.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AdminAccountInitializerTest {

    private static final String ADMIN_USERNAME = "admin01";
    private static final String ADMIN_EMAIL = "admin@correo.com";
    private static final String ADMIN_PASSWORD = "SecretaDelAdmin2026";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int BALANCE_SCALE = 2;

    private static final String MISSING_PASSWORD_WARNING = "Falta la contraseña del administrador";
    private static final String MISSING_DATA_WARNING = "Falta el nombre de usuario o el correo";
    private static final String EXISTING_USERNAME_INFO = "el administrador no se modifica";
    private static final String EMAIL_TAKEN_WARNING = "El correo del administrador ya está registrado";
    private static final String RACE_WARNING = "Otra cuenta tomó el nombre de usuario o el correo";

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();
    private final ApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void sinNingunaPropiedadDelAdministradorAdvierteYNoGuarda(CapturedOutput output) {
        initializer(null).run(args);

        assertSkippedWith(output, MISSING_PASSWORD_WARNING);
    }

    @Test
    void sinContrasenaAdvierteYNoGuarda(CapturedOutput output) {
        initializerWith(ADMIN_USERNAME, ADMIN_EMAIL, null).run(args);

        assertSkippedWith(output, MISSING_PASSWORD_WARNING);
    }

    @Test
    void sinNombreDeUsuarioAdvierteYNoGuarda(CapturedOutput output) {
        initializerWith(" ", ADMIN_EMAIL, ADMIN_PASSWORD).run(args);

        assertSkippedWith(output, MISSING_DATA_WARNING);
    }

    @Test
    void sinCorreoAdvierteYNoGuarda(CapturedOutput output) {
        initializerWith(ADMIN_USERNAME, null, ADMIN_PASSWORD).run(args);

        assertSkippedWith(output, MISSING_DATA_WARNING);
    }

    /**
     * La comparación sin distinguir mayúsculas es del repository y la cubre
     * {@code AppUserRepositoryIT}: acá alcanza con que el username figure como existente, sea de
     * quien sea.
     */
    @Test
    void siElNombreDeUsuarioYaExisteNoTocaNada(CapturedOutput output) {
        when(appUserRepository.existsByUsername(ADMIN_USERNAME)).thenReturn(true);

        initializerWith(ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD).run(args);

        assertSkippedWith(output, EXISTING_USERNAME_INFO);
    }

    @Test
    void siElCorreoEstaTomadoAdvierteYNoGuarda(CapturedOutput output) {
        when(appUserRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(true);

        initializerWith(ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD).run(args);

        assertSkippedWith(output, EMAIL_TAKEN_WARNING);
    }

    @Test
    void conUnNombreDeUsuarioInvalidoAdvierteConLaReglaYNoGuarda(CapturedOutput output) {
        initializerWith("admin-01", ADMIN_EMAIL, ADMIN_PASSWORD).run(args);

        assertSkippedWith(output, CredentialPolicy.USERNAME_FORMAT_MESSAGE);
    }

    @Test
    void conLaConfiguracionCompletaGuardaUnAdministradorConSaldoCeroYSinClave(CapturedOutput output) {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        initializerWith(ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD).run(args);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        AppUser admin = saved.getValue();
        assertThat(admin.getUsername()).isEqualTo(ADMIN_USERNAME);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(admin.getBalance().scale()).isEqualTo(BALANCE_SCALE);
        assertThat(admin.getApiKeyHash()).isNull();
        assertPasswordNotLogged(output);
    }

    @Test
    void siUnRegistroGanaLaCarreraAdvierteSinPropagar(CapturedOutput output) {
        when(appUserRepository.save(any(AppUser.class))).thenThrow(new DuplicateUsernameException());

        assertThatCode(() -> initializerWith(ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD).run(args))
                .doesNotThrowAnyException();

        assertThat(output.getAll()).contains(RACE_WARNING);
        assertPasswordNotLogged(output);
    }

    private void assertSkippedWith(CapturedOutput output, String expectedLog) {
        verify(appUserRepository, never()).save(any(AppUser.class));
        assertThat(output.getAll()).contains(expectedLog);
        assertPasswordNotLogged(output);
    }

    private void assertPasswordNotLogged(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD);
    }

    private AdminAccountInitializer initializerWith(String username, String email, String password) {
        return initializer(new AuthProperties.Admin(username, email, password));
    }

    private AdminAccountInitializer initializer(AuthProperties.Admin admin) {
        return new AdminAccountInitializer(new AuthProperties(INITIAL_BALANCE, admin), appUserRepository,
                passwordHasher);
    }
}
