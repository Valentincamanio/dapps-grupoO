package ar.edu.unq.desapp.futbolmarket.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final long USER_ID = 7L;
    private static final long UNKNOWN_USER_ID = 999L;

    @Mock
    private AppUserRepository appUserRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(appUserRepository);
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
}
