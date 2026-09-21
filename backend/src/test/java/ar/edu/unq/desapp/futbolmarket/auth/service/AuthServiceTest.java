package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.ApiKey;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegisteredUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionTokenIssuer;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameAndEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USERNAME = "lionel10";
    private static final String EMAIL = "lionel@correo.com";
    private static final String PASSWORD = "campeon2022";
    private static final String WRONG_PASSWORD = "otraClave123";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas.";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int API_KEY_LENGTH = 43;
    private static final long UNKNOWN_USER_ID = 999L;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private SessionTokenIssuer sessionTokenIssuer;

    @Mock
    private PasswordHasher mockedPasswordHasher;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(appUserRepository, passwordHasher, sessionTokenIssuer,
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

    @Test
    void elLoginDevuelveElTokenQueEmiteElEmisor() {
        AppUser user = existingUser();
        SessionToken expected = new SessionToken("un.token.firmado", Instant.parse("2026-09-19T15:00:00Z"));
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(sessionTokenIssuer.issueFor(user)).thenReturn(expected);

        assertThat(authService.login(USERNAME, PASSWORD)).isEqualTo(expected);
    }

    @Test
    void elLoginLanzaInvalidCredentialsConUnaContrasenaIncorrecta() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.login(USERNAME, WRONG_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(INVALID_CREDENTIALS_MESSAGE);
        verify(sessionTokenIssuer, never()).issueFor(any(AppUser.class));
    }

    @Test
    void elLoginLanzaLaMismaExcepcionYMensajeCuandoElUsuarioNoExiste() {
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(USERNAME, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(INVALID_CREDENTIALS_MESSAGE);
        verify(sessionTokenIssuer, never()).issueFor(any(AppUser.class));
    }

    /**
     * Con un usuario inexistente no hay nada que comparar, pero igual se verifica contra un hash
     * descartable: sin esa verificación, la respuesta sería más rápida y delataría que el usuario
     * no existe (research D5 y SC-007).
     */
    @Test
    void elLoginVerificaLaContrasenaAunqueElUsuarioNoExista() {
        AuthService serviceWithMockedHasher = new AuthService(appUserRepository, mockedPasswordHasher,
                sessionTokenIssuer, new AuthProperties(INITIAL_BALANCE, null));
        when(appUserRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithMockedHasher.login(USERNAME, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(mockedPasswordHasher).matches(eq(PASSWORD), any());
    }

    @Test
    void findByApiKeyBuscaPorElHashDeLaClaveEnClaro() {
        ApiKey apiKey = ApiKey.generate();
        AppUser user = existingUser();
        when(appUserRepository.findByApiKeyHash(apiKey.hash())).thenReturn(Optional.of(user));

        assertThat(authService.findByApiKey(apiKey.value())).contains(user);
    }

    @Test
    void findByApiKeyNoEncuentraNadaConUnaClaveDesconocida() {
        when(appUserRepository.findByApiKeyHash(any())).thenReturn(Optional.empty());

        assertThat(authService.findByApiKey(ApiKey.generate().value())).isEmpty();
    }

    @Test
    void findByIdNoEncuentraNadaConUnIdInexistente() {
        when(appUserRepository.findById(UNKNOWN_USER_ID)).thenReturn(Optional.empty());

        assertThat(authService.findById(UNKNOWN_USER_ID)).isEmpty();
    }

    private void givenAvailability(boolean usernameTaken, boolean emailTaken) {
        when(appUserRepository.existsByUsername(USERNAME)).thenReturn(usernameTaken);
        when(appUserRepository.existsByEmail(EMAIL)).thenReturn(emailTaken);
    }

    private AppUser existingUser() {
        return AppUser.register(USERNAME, EMAIL, PASSWORD, INITIAL_BALANCE, passwordHasher).user();
    }
}
