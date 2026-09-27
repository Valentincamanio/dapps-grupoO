package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los predicados son la única fuente de las reglas de formato: los comparten el modelo y los DTO.
 * Se prueban acá de forma directa porque {@code AppUser} filtra los casos nulos y de longitud
 * antes de invocarlos, así que por ese camino nunca se los ejercita completos.
 */
class CredentialPolicyTest {

    private static final String VALID_USERNAME = "lionel10";
    private static final String VALID_EMAIL = "lionel@correo.com";
    private static final String VALID_PASSWORD = "campeon2022";

    @ParameterizedTest
    @ValueSource(strings = {"abc", VALID_USERNAME, "con_guion_bajo_10"})
    void aceptaLosNombresDeUsuarioConLetrasNumerosYGuionBajo(String username) {
        assertThat(CredentialPolicy.isValidUsername(username)).isTrue();
    }

    @Test
    void aceptaUnNombreDeUsuarioDeTreintaCaracteres() {
        assertThat(CredentialPolicy.isValidUsername("a".repeat(CredentialPolicy.USERNAME_MAX_LENGTH))).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "lionel 10", "lionel-10", "lionél10"})
    void rechazaLosNombresDeUsuarioQueNoCumplenLaRegla(String username) {
        assertThat(CredentialPolicy.isValidUsername(username)).isFalse();
    }

    @Test
    void rechazaUnNombreDeUsuarioDeTreintaYUnCaracteres() {
        assertThat(CredentialPolicy.isValidUsername("a".repeat(CredentialPolicy.USERNAME_MAX_LENGTH + 1))).isFalse();
    }

    @Test
    void aceptaUnCorreoBienFormado() {
        assertThat(CredentialPolicy.isValidEmail(VALID_EMAIL)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"correo-invalido", "sin@dominio", "dos@@arrobas.com", "con espacio@correo.com"})
    void rechazaLosCorreosMalFormados(String email) {
        assertThat(CredentialPolicy.isValidEmail(email)).isFalse();
    }

    @Test
    void rechazaUnCorreoQueSuperaElLargoMaximo() {
        String domain = "@correo.com";
        String email = "a".repeat(CredentialPolicy.EMAIL_MAX_LENGTH - domain.length() + 1) + domain;

        assertThat(email).hasSize(CredentialPolicy.EMAIL_MAX_LENGTH + 1);
        assertThat(CredentialPolicy.isValidEmail(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"clave123", VALID_PASSWORD})
    void aceptaLasContrasenasDesdeElMinimo(String password) {
        assertThat(CredentialPolicy.isValidPassword(password)).isTrue();
    }

    @Test
    void aceptaUnaContrasenaDeSetentaYDosCaracteresSimples() {
        assertThat(CredentialPolicy.isValidPassword("a".repeat(CredentialPolicy.PASSWORD_MAX_LENGTH))).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"clave12"})
    void rechazaLasContrasenasMasCortasQueElMinimo(String password) {
        assertThat(CredentialPolicy.isValidPassword(password)).isFalse();
    }

    /** El máximo se mide en bytes UTF-8: 40 emojis de 4 bytes superan los 72 con 40 caracteres. */
    @Test
    void rechazaUnaContrasenaQueSuperaLosSetentaYDosBytesAunqueTengaMenosCaracteres() {
        String password = "😀".repeat(40);

        assertThat(password.codePointCount(0, password.length())).isLessThan(CredentialPolicy.PASSWORD_MAX_LENGTH);
        assertThat(CredentialPolicy.isValidPassword(password)).isFalse();
    }
}
