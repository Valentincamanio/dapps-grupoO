package ar.edu.unq.desapp.futbolmarket.persistence.repository.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.user.AppUserMapper;

/**
 * El conteo de cuentas que lee el gauge {@code futbolmarket.users.registered} (research D7 de la
 * 012). Se mide la diferencia antes/después, sin suponer una base vacía.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({AppUserRepository.class, AppUserMapper.class})
class UserCountIT {

    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";

    @Autowired
    private AppUserRepository repository;

    @Test
    void guardarUnUsuarioSumaUnoAlConteo() {
        long before = repository.count();

        repository.save(AppUser.reconstitute(null, "contado", "contado@correo.com", PASSWORD_HASH, null, Role.USER,
                new BigDecimal("1000.00")));

        assertThat(repository.count()).isEqualTo(before + 1);
    }
}
