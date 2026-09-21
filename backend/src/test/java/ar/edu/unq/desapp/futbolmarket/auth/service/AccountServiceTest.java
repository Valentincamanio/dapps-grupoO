package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidPasswordChangeException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final long USER_ID = 7L;
    private static final long UNKNOWN_USER_ID = 999L;
    private static final String PASSWORD = "campeon2022";
    private static final String NEW_PASSWORD = "tricampeon2022";

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(appUserRepository, passwordHasher);
    }

    @Test
    void getProfileDevuelveElUsuarioDelId() {
        AppUser user = AppUser.reconstitute(USER_ID, "lionel10", "lionel@correo.com", "hash", null,
                Role.USER, new BigDecimal("1000.00"));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(accountService.getProfile(USER_ID)).isSameAs(user);
    }

    @Test
    void getProfileLanzaInvalidCredentialsConUnIdInexistente() {
        when(appUserRepository.findById(UNKNOWN_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getProfile(UNKNOWN_USER_ID))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unCambioDeContrasenaValidoGuardaElUsuario() {
        AppUser user = registeredUser();
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        accountService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD);

        verify(appUserRepository).save(user);
    }

    @Test
    void siElModeloRechazaElCambioNoSeGuardaNada() {
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(registeredUser()));

        assertThatThrownBy(() -> accountService.changePassword(USER_ID, "otraClave123", NEW_PASSWORD))
                .isInstanceOf(InvalidPasswordChangeException.class);
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    private AppUser registeredUser() {
        return AppUser.register("lionel10", "lionel@correo.com", PASSWORD, new BigDecimal("1000.00"),
                passwordHasher).user();
    }
}
