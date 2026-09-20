package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegisteredUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameAndEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USERNAME = "lionel10";
    private static final String EMAIL = "lionel@correo.com";
    private static final String PASSWORD = "campeon2022";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int API_KEY_LENGTH = 43;

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(appUserRepository, passwordHasher,
                new AuthProperties(INITIAL_BALANCE, null));
    }

    @Test
    void registraUnUsuarioComunConElSaldoConfiguradoYDevuelveLaClave() {
        givenAvailability(false, false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        RegisteredUser registered = authService.register(USERNAME, EMAIL, PASSWORD);

        assertThat(registered.user().getUsername()).isEqualTo(USERNAME);
        assertThat(registered.user().getRole()).isEqualTo(Role.USER);
        assertThat(registered.user().getBalance()).isEqualByComparingTo(INITIAL_BALANCE);
        assertThat(registered.apiKey().value()).hasSize(API_KEY_LENGTH);
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void lanzaDuplicateUsernameYNoGuardaCuandoElNombreDeUsuarioEstaTomado() {
        givenAvailability(true, false);

        assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void lanzaDuplicateEmailYNoGuardaCuandoElCorreoEstaTomado() {
        givenAvailability(false, true);

        assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(DuplicateEmailException.class);
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void lanzaDuplicateUsernameAndEmailYNoGuardaCuandoLosDosEstanTomados() {
        givenAvailability(true, true);

        assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(DuplicateUsernameAndEmailException.class);
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    private void givenAvailability(boolean usernameTaken, boolean emailTaken) {
        when(appUserRepository.existsByUsername(USERNAME)).thenReturn(usernameTaken);
        when(appUserRepository.existsByEmail(EMAIL)).thenReturn(emailTaken);
    }
}
