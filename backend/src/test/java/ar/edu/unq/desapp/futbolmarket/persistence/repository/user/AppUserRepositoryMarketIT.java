package ar.edu.unq.desapp.futbolmarket.persistence.repository.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketBusyException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;

/**
 * Lo que la 008 suma al repository de cuentas: el saldo reservado y la fecha de registro, los
 * bloqueos del mercado y el superusuario (research D5 a D7 y D13). Los tests de la 001 siguen en
 * {@code AppUserRepositoryIT}, sin cambios.
 *
 * <p>Con una H2 propia y un tiempo de espera de bloqueo corto, para que el caso de la cuenta
 * ocupada no demore el build. Los bloqueos duran lo que la transacción de quien los pide, así que
 * cada uno corre dentro de una.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:app-user-repository-market-it;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500")
class AppUserRepositoryMarketIT {
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal NOTHING_RESERVED = new BigDecimal("0.00");
    private static final Instant REGISTERED_AT = Instant.parse("2026-08-18T02:30:00Z");
    private static final long MISSING_ID = 999_999L;
    private static final long LATCH_TIMEOUT_SECONDS = 10;

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private AppUserSQLDAO appUserDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void prepareDatabase() {
        appUserDAO.deleteAll();
    }

    @AfterEach
    void cleanDatabase() {
        appUserDAO.deleteAll();
    }

    @Test
    void guardaYRecuperaElReservadoYLaFechaDeRegistro() {
        AppUser user = AppUser.reconstitute(null, "lionel10", "lionel@correo.com", PASSWORD_HASH, null, Role.USER,
                INITIAL_BALANCE, new BigDecimal("10.30"), REGISTERED_AT);

        AppUser saved = repository.save(user);
        AppUser found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getReserved()).isEqualTo(new BigDecimal("10.30"));
        assertThat(found.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(found.availableBalance()).isEqualTo(new BigDecimal("989.70"));
    }

    @Test
    void unaCuentaSinFechaDeRegistroSeGuardaSinReservaYSinFecha() {
        AppUser saved = repository.save(account("angel11", Role.USER));

        AppUser found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getReserved()).isEqualTo(NOTHING_RESERVED);
        assertThat(found.getRegisteredAt()).isNull();
    }

    @Test
    void bloquearUnaCuentaLaDevuelveConSuSaldo() {
        long id = save("lionel10", Role.USER);

        Optional<AppUser> locked = transactionTemplate.execute(status -> repository.lockById(id));

        assertThat(locked).hasValueSatisfying(user -> {
            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getBalance()).isEqualTo(INITIAL_BALANCE);
        });
    }

    @Test
    void unaCuentaInexistenteNoSeBloquea() {
        Optional<AppUser> locked = transactionTemplate.execute(status -> repository.lockById(MISSING_ID));

        assertThat(locked).isEmpty();
    }

    @Test
    void bloqueaLasCuentasDeMenorAMayorIdSinRepetirYSinLasInexistentes() {
        long first = save("ana", Role.USER);
        long second = save("beto", Role.USER);
        long third = save("carla", Role.USER);

        Map<Long, AppUser> locked = transactionTemplate.execute(status ->
                repository.lockAllInOrder(List.of(third, first, MISSING_ID, second, first)));

        assertThat(locked.keySet()).containsExactly(first, second, third);
        assertThat(locked.get(second).getUsername()).isEqualTo("beto");
    }

    @Test
    void sinIdsNoSeBloqueaNingunaCuenta() {
        Map<Long, AppUser> locked = transactionTemplate.execute(status -> repository.lockAllInOrder(List.of()));

        assertThat(locked).isEmpty();
    }

    @Test
    void elSuperusuarioEsLaPrimeraCuentaAdministrador() {
        save("lionel10", Role.USER);
        long firstAdmin = save("admin1", Role.ADMIN);
        save("admin2", Role.ADMIN);

        Optional<AppUser> superuser = repository.findSuperuser();

        assertThat(superuser).hasValueSatisfying(user -> assertThat(user.getId()).isEqualTo(firstAdmin));
    }

    @Test
    void sinCuentaAdministradorNoHaySuperusuario() {
        save("lionel10", Role.USER);

        Optional<AppUser> superuser = repository.findSuperuser();

        assertThat(superuser).isEmpty();
    }

    @Test
    void unaCuentaQueOtroPedidoTieneBloqueadaRespondeQueElMercadoEstaOcupado() throws Exception {
        long id = save("lionel10", Role.USER);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> otherRequest = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.lockById(id);
            locked.countDown();
            await(release);
        }));
        await(locked);

        try {
            Throwable thrown = catchThrowable(() ->
                    transactionTemplate.executeWithoutResult(status -> repository.lockById(id)));

            assertThat(thrown)
                    .isInstanceOf(MarketBusyException.class)
                    .hasMessage("El mercado está ocupado. Vuelva a intentar en unos segundos.");
        } finally {
            release.countDown();
            otherRequest.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();
        }
    }

    private long save(String username, Role role) {
        return repository.save(account(username, role)).getId();
    }

    private static AppUser account(String username, Role role) {
        return AppUser.reconstitute(null, username, username + "@correo.com", PASSWORD_HASH, null, role,
                INITIAL_BALANCE);
    }

    /**
     * Sin excepciones chequeadas, porque se usa también dentro de la transacción del otro pedido.
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El otro pedido no avanzó a tiempo.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
