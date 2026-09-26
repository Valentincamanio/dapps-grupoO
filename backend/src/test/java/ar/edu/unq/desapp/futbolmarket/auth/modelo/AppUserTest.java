package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidPasswordChangeException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidUserDataException;

class AppUserTest {

    private static final String USERNAME = "lionel10";
    private static final String EMAIL = "lionel@correo.com";
    private static final String PASSWORD = "campeon2022";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final int BALANCE_SCALE = 2;
    private static final String EMAIL_DOMAIN = "@correo.com";
    private static final String NEW_PASSWORD = "tricampeon2022";
    private static final String WRONG_CURRENT_PASSWORD_MESSAGE = "La contraseña actual es incorrecta.";
    private static final String SAME_PASSWORD_MESSAGE = "La nueva contraseña debe ser distinta de la actual.";
    private static final String INVALID_NEW_PASSWORD_MESSAGE = "La nueva contraseña no cumple las reglas de formato.";

    private final PasswordHasher hasher = new FakePasswordHasher();

    @Test
    void elRegistroCreaUnUsuarioComunConElSaldoInicial() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getBalance()).isEqualByComparingTo(INITIAL_BALANCE);
        assertThat(user.getBalance().scale()).isEqualTo(BALANCE_SCALE);
    }

    @Test
    void elRegistroNormalizaElSaldoAEscalaDos() {
        AppUser user = AppUser.register(USERNAME, EMAIL, PASSWORD, new BigDecimal("1000"), hasher).user();

        assertThat(user.getBalance().scale()).isEqualTo(BALANCE_SCALE);
    }

    @Test
    void elRegistroGuardaElHashYNoLaContrasenaEnClaro() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        assertThat(user.getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .isEqualTo(hasher.hash(PASSWORD));
    }

    @Test
    void elRegistroEmiteUnaClaveDeApiCuyoHashQueda() {
        RegisteredUser registered = register(USERNAME, EMAIL, PASSWORD);

        assertThat(registered.user().getApiKeyHash())
                .isEqualTo(ApiKey.hashOf(registered.apiKey().value()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "lionel10", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void aceptaLosNombresDeUsuarioValidos(String username) {
        assertThatCode(() -> register(username, EMAIL, PASSWORD)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "lionel 10", "lionel-10", "lionél10"})
    void rechazaLosNombresDeUsuarioInvalidos(String username) {
        assertThatThrownBy(() -> register(username, EMAIL, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void aceptaUnCorreoDeDoscientosCincuentaYCuatroCaracteres() {
        String email = localPartOfLength(254 - EMAIL_DOMAIN.length()) + EMAIL_DOMAIN;

        assertThat(email).hasSize(254);
        assertThatCode(() -> register(USERNAME, email, PASSWORD)).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnCorreoDeDoscientosCincuentaYCincoCaracteres() {
        String email = localPartOfLength(255 - EMAIL_DOMAIN.length()) + EMAIL_DOMAIN;

        assertThatThrownBy(() -> register(USERNAME, email, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "dos@@arrobas.com", "sin@dominio", "con espacio@correo.com"})
    void rechazaLosCorreosMalFormados(String email) {
        assertThatThrownBy(() -> register(USERNAME, email, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void aceptaLasContrasenasDeOchoYDeSetentaYDosCaracteresSimples() {
        assertThatCode(() -> register(USERNAME, EMAIL, "clave123")).doesNotThrowAnyException();
        assertThatCode(() -> register(USERNAME, EMAIL, "a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnaContrasenaDeSieteCaracteres() {
        assertThatThrownBy(() -> register(USERNAME, EMAIL, "clave12"))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void rechazaUnaContrasenaQueSuperaLosSetentaYDosBytesAunqueTengaMenosCaracteres() {
        String password = "ñ".repeat(40);

        assertThat(password).hasSizeLessThan(72);
        assertThatThrownBy(() -> register(USERNAME, EMAIL, password))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void elMensajeDeErrorNoRepiteElValorRecibido() {
        assertThatThrownBy(() -> register("Qx", EMAIL, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageNotContaining("Qx");

        assertThatThrownBy(() -> register(USERNAME, EMAIL, "Zq7w"))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageNotContaining("Zq7w");
    }

    @Test
    void emitirUnaClaveNuevaInvalidaLaAnterior() {
        RegisteredUser registered = register(USERNAME, EMAIL, PASSWORD);
        AppUser user = registered.user();
        String previousHash = user.getApiKeyHash();

        ApiKey renewed = user.issueApiKey();

        assertThat(user.getApiKeyHash())
                .isEqualTo(renewed.hash())
                .isNotEqualTo(previousHash);
        assertThat(ApiKey.hashOf(registered.apiKey().value())).isNotEqualTo(user.getApiKeyHash());
    }

    @Test
    void elToStringNoMuestraNingunoDeLosDosHashes() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        assertThat(user.toString())
                .doesNotContain(user.getPasswordHash())
                .doesNotContain(user.getApiKeyHash());
    }

    @Test
    void verifyPasswordAceptaLaContrasenaCorrecta() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        assertThatCode(() -> user.verifyPassword(PASSWORD, hasher)).doesNotThrowAnyException();
    }

    @Test
    void verifyPasswordLanzaInvalidCredentialsConUnaContrasenaIncorrecta() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        assertThatThrownBy(() -> user.verifyPassword("otraClave123", hasher))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas.");
    }

    @Test
    void unCambioValidoHaceQueVerifiqueLaNuevaYNoLaAnterior() {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();

        user.changePassword(PASSWORD, NEW_PASSWORD, hasher);

        assertThatCode(() -> user.verifyPassword(NEW_PASSWORD, hasher)).doesNotThrowAnyException();
        assertThatThrownBy(() -> user.verifyPassword(PASSWORD, hasher))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void elCambioSeRechazaSinTocarNadaSiLaActualEsIncorrecta() {
        assertPasswordChangeRejected("otraClave123", NEW_PASSWORD, WRONG_CURRENT_PASSWORD_MESSAGE);
    }

    @Test
    void elCambioSeRechazaSinTocarNadaSiLaNuevaEsIgualALaActual() {
        assertPasswordChangeRejected(PASSWORD, PASSWORD, SAME_PASSWORD_MESSAGE);
    }

    @Test
    void elCambioSeRechazaSinTocarNadaSiLaNuevaTieneSieteCaracteres() {
        assertPasswordChangeRejected(PASSWORD, "clave12", INVALID_NEW_PASSWORD_MESSAGE);
    }

    @Test
    void elCambioSeRechazaSinTocarNadaSiLaNuevaSuperaLosSetentaYDosBytes() {
        assertPasswordChangeRejected(PASSWORD, "ñ".repeat(40), INVALID_NEW_PASSWORD_MESSAGE);
    }

    /** El orden de los controles decide qué se informa cuando fallan dos a la vez. */
    @Test
    void conLaActualIncorrectaYLaNuevaInvalidaSeInformaLaActualIncorrecta() {
        assertPasswordChangeRejected("otraClave123", "corta", WRONG_CURRENT_PASSWORD_MESSAGE);
    }

    @Test
    void createAdminCreaUnAdministradorConSaldoCeroYSinClave() {
        AppUser admin = AppUser.createAdmin(USERNAME, EMAIL, PASSWORD, hasher);

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(admin.getBalance().scale()).isEqualTo(BALANCE_SCALE);
        assertThat(admin.getApiKeyHash()).isNull();
        assertThat(admin.getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .isEqualTo(hasher.hash(PASSWORD));
    }

    @Test
    void createAdminRechazaUnNombreDeUsuarioConGuionMedio() {
        assertThatThrownBy(() -> AppUser.createAdmin("admin-01", EMAIL, PASSWORD, hasher))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void createAdminRechazaUnCorreoMalFormado() {
        assertThatThrownBy(() -> AppUser.createAdmin(USERNAME, "sin-arroba", PASSWORD, hasher))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void createAdminRechazaUnaContrasenaDeSieteCaracteres() {
        assertThatThrownBy(() -> AppUser.createAdmin(USERNAME, EMAIL, "clave12", hasher))
                .isInstanceOf(InvalidUserDataException.class);
    }

    /**
     * El DTO ataja los campos vacios con Bean Validation, pero la invariante vive en el modelo:
     * `register` y `createAdmin` tambien se invocan desde el alta del administrador, con lo que
     * venga de la configuracion.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnNombreDeUsuarioAusenteOEnBlanco(String username) {
        assertThatThrownBy(() -> register(username, EMAIL, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(CredentialPolicy.USERNAME_REQUIRED_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnCorreoAusenteOEnBlanco(String email) {
        assertThatThrownBy(() -> register(USERNAME, email, PASSWORD))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(CredentialPolicy.EMAIL_REQUIRED_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        "})
    void rechazaUnaContrasenaAusenteOEnBlanco(String password) {
        assertThatThrownBy(() -> register(USERNAME, EMAIL, password))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessage(CredentialPolicy.PASSWORD_REQUIRED_MESSAGE);
    }

    private void assertPasswordChangeRejected(String currentPassword, String newPassword,
                                              String expectedMessage) {
        AppUser user = register(USERNAME, EMAIL, PASSWORD).user();
        String previousHash = user.getPasswordHash();

        assertThatThrownBy(() -> user.changePassword(currentPassword, newPassword, hasher))
                .isInstanceOf(InvalidPasswordChangeException.class)
                .hasMessage(expectedMessage);
        assertThat(user.getPasswordHash()).isEqualTo(previousHash);
    }

    private RegisteredUser register(String username, String email, String password) {
        return AppUser.register(username, email, password, INITIAL_BALANCE, hasher);
    }

    private String localPartOfLength(int length) {
        return "a".repeat(length);
    }
}
