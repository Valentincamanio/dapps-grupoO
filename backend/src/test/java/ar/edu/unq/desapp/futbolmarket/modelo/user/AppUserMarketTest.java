package ar.edu.unq.desapp.futbolmarket.modelo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.modelo.auth.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.TradingNotAllowedException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.AccountAccessDeniedException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.DateBeforeRegistrationException;

/**
 * Lo que la 008 suma a la cuenta: el saldo reservado, los permisos del mercado y la fecha de
 * registro. Los tests de la 001 siguen en {@code AppUserTest}, sin cambios.
 */
class AppUserMarketTest {

    private static final long OWNER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long ADMIN_ID = 1L;
    private static final String USERNAME = "lionel10";
    private static final String EMAIL = "lionel@correo.com";
    private static final String PASSWORD = "campeon2022";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal NOTHING_RESERVED = new BigDecimal("0.00");
    /** El 17/08 a las 23:30 en Argentina, que en UTC ya es el 18/08. */
    private static final Instant REGISTERED_AT = Instant.parse("2026-08-18T02:30:00Z");

    private final PasswordHasher hasher = new FakePasswordHasher();

    @Test
    void reservarPasaCreditosDelLibreAlReservadoSinMoverElTotal() {
        AppUser user = userWith(INITIAL_BALANCE);

        user.reserve(new BigDecimal("10.30"));

        assertThat(user.getBalance()).isEqualTo(INITIAL_BALANCE);
        assertThat(user.getReserved()).isEqualTo(new BigDecimal("10.30"));
        assertThat(user.availableBalance()).isEqualTo(new BigDecimal("989.70"));
    }

    @Test
    void pagarConLaReservaYAjustarElRemanenteTomaLaDiferenciaDelLibre() {
        AppUser user = userWith(INITIAL_BALANCE);
        user.reserve(new BigDecimal("10.30"));
        user.payFromReserve(new BigDecimal("4.20"));

        user.reserve(new BigDecimal("0.08"));

        assertThat(user.getBalance()).isEqualTo(new BigDecimal("995.80"));
        assertThat(user.getReserved()).isEqualTo(new BigDecimal("6.18"));
        assertThat(user.availableBalance()).isEqualTo(new BigDecimal("989.62"));
    }

    @Test
    void reservarMasQueElLibreSeRechazaSinCambiarNada() {
        AppUser user = userWith(new BigDecimal("5.00"));

        assertThatThrownBy(() -> user.reserve(new BigDecimal("10.30")))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,30 créditos y el saldo libre es 5,00.");
        assertThat(user.getBalance()).isEqualTo(new BigDecimal("5.00"));
        assertThat(user.getReserved()).isEqualTo(NOTHING_RESERVED);
    }

    @Test
    void loYaReservadoNoCuentaComoLibre() {
        AppUser user = userWith(INITIAL_BALANCE);
        user.reserve(new BigDecimal("995.00"));

        assertThatThrownBy(() -> user.reserve(new BigDecimal("10.00")))
                .isInstanceOf(InsufficientCreditsException.class)
                .hasMessage("Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.");
    }

    @Test
    void liberarDevuelveLaReservaAlLibre() {
        AppUser user = userWith(INITIAL_BALANCE);
        user.reserve(new BigDecimal("10.30"));

        user.release(new BigDecimal("10.30"));

        assertThat(user.getReserved()).isEqualTo(NOTHING_RESERVED);
        assertThat(user.availableBalance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    void liberarMasQueLoReservadoEsUnaInvariante() {
        AppUser user = userWith(INITIAL_BALANCE);
        user.reserve(new BigDecimal("10.30"));

        assertThatThrownBy(() -> user.release(new BigDecimal("10.31")))
                .isInstanceOf(MarketInvariantException.class);
        assertThat(user.getReserved()).isEqualTo(new BigDecimal("10.30"));
    }

    @Test
    void pagarMasQueLoReservadoEsUnaInvariante() {
        AppUser user = userWith(INITIAL_BALANCE);
        user.reserve(new BigDecimal("10.30"));

        assertThatThrownBy(() -> user.payFromReserve(new BigDecimal("10.31")))
                .isInstanceOf(MarketInvariantException.class);
        assertThat(user.getBalance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    void cobrarSumaAlTotalYAlLibre() {
        AppUser user = userWith(INITIAL_BALANCE);

        user.receive(new BigDecimal("10.00"));

        assertThat(user.getBalance()).isEqualTo(new BigDecimal("1010.00"));
        assertThat(user.availableBalance()).isEqualTo(new BigDecimal("1010.00"));
    }

    @Test
    void cobrarCeroEsUnaInvariante() {
        AppUser user = userWith(INITIAL_BALANCE);

        assertThatThrownBy(() -> user.receive(NOTHING_RESERVED))
                .isInstanceOf(MarketInvariantException.class);
    }

    @Test
    void unUsuarioComunOpera() {
        AppUser user = userWith(INITIAL_BALANCE);

        assertThatCode(user::requireCanTrade).doesNotThrowAnyException();
    }

    @Test
    void elAdministradorNoOperaEnElMercado() {
        AppUser admin = admin();

        assertThatThrownBy(admin::requireCanTrade)
                .isInstanceOf(TradingNotAllowedException.class)
                .hasMessage("El administrador no opera en el mercado.");
    }

    @Test
    void elDuenoAccedeASuCuenta() {
        AppUser user = userWith(INITIAL_BALANCE);

        assertThatCode(() -> user.requireCanAccessAccountOf(OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    void elAdministradorAccedeACualquierCuenta() {
        AppUser admin = admin();

        assertThatCode(() -> admin.requireCanAccessAccountOf(OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    void otroUsuarioNoAccedeALaCuenta() {
        AppUser user = userWith(INITIAL_BALANCE);

        assertThatThrownBy(() -> user.requireCanAccessAccountOf(OTHER_USER_ID))
                .isInstanceOf(AccountAccessDeniedException.class)
                .hasMessage("No tiene permiso para ver la cuenta de otro usuario.");
    }

    @Test
    void elRegistroGuardaSuInstante() {
        AppUser user = AppUser.register(USERNAME, EMAIL, PASSWORD, INITIAL_BALANCE, hasher, REGISTERED_AT).user();

        assertThat(user.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(user.getReserved()).isEqualTo(NOTHING_RESERVED);
    }

    @Test
    void elDiaDelRegistroEnHoraDeArgentinaEstaAdmitido() {
        AppUser user = AppUser.register(USERNAME, EMAIL, PASSWORD, INITIAL_BALANCE, hasher, REGISTERED_AT).user();

        assertThatCode(() -> user.requireRegisteredBy(LocalDate.of(2026, 8, 17))).doesNotThrowAnyException();
    }

    @Test
    void unDiaAnteriorAlRegistroSeRechaza() {
        AppUser user = AppUser.register(USERNAME, EMAIL, PASSWORD, INITIAL_BALANCE, hasher, REGISTERED_AT).user();

        assertThatThrownBy(() -> user.requireRegisteredBy(LocalDate.of(2026, 8, 16)))
                .isInstanceOf(DateBeforeRegistrationException.class)
                .hasMessage("La fecha 2026-08-16 es anterior al registro del usuario (2026-08-17).");
    }

    @Test
    void unaCuentaSinFechaDeRegistroAdmiteCualquierDia() {
        AppUser user = userWith(INITIAL_BALANCE);

        assertThatCode(() -> user.requireRegisteredBy(LocalDate.of(2020, 1, 1))).doesNotThrowAnyException();
    }

    @Test
    void elAltaDelAdministradorGuardaSuInstante() {
        AppUser admin = AppUser.createAdmin("admin", "admin@correo.com", PASSWORD, hasher, REGISTERED_AT);

        assertThat(admin.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(admin.getReserved()).isEqualTo(NOTHING_RESERVED);
    }

    @Test
    void elRegistroDeLaUnoNoReservaNadaNiTieneFecha() {
        AppUser user = AppUser.register(USERNAME, EMAIL, PASSWORD, INITIAL_BALANCE, hasher).user();

        assertThat(user.getReserved()).isEqualTo(NOTHING_RESERVED);
        assertThat(user.getRegisteredAt()).isNull();
    }

    @Test
    void elAltaDelAdministradorDeLaUnoNoReservaNadaNiTieneFecha() {
        AppUser admin = AppUser.createAdmin("admin", "admin@correo.com", PASSWORD, hasher);

        assertThat(admin.getReserved()).isEqualTo(NOTHING_RESERVED);
        assertThat(admin.getRegisteredAt()).isNull();
    }

    @Test
    void laReconstruccionDeSieteArgumentosNoReservaNadaNiTieneFecha() {
        AppUser user = AppUser.reconstitute(OWNER_ID, USERNAME, EMAIL, hasher.hash(PASSWORD), null, Role.USER,
                INITIAL_BALANCE);

        assertThat(user.getReserved()).isEqualTo(NOTHING_RESERVED);
        assertThat(user.getRegisteredAt()).isNull();
        assertThat(user.availableBalance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    void laReconstruccionDeNueveArgumentosConservaElReservadoYLaFecha() {
        AppUser user = AppUser.reconstitute(OWNER_ID, USERNAME, EMAIL, hasher.hash(PASSWORD), null, Role.USER,
                INITIAL_BALANCE, new BigDecimal("10.30"), REGISTERED_AT);

        assertThat(user.getReserved()).isEqualTo(new BigDecimal("10.30"));
        assertThat(user.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(user.availableBalance()).isEqualTo(new BigDecimal("989.70"));
    }

    private AppUser userWith(BigDecimal balance) {
        return AppUser.reconstitute(OWNER_ID, USERNAME, EMAIL, hasher.hash(PASSWORD), null, Role.USER, balance);
    }

    private AppUser admin() {
        return AppUser.reconstitute(ADMIN_ID, "admin", "admin@correo.com", hasher.hash(PASSWORD), null, Role.ADMIN,
                NOTHING_RESERVED);
    }
}
