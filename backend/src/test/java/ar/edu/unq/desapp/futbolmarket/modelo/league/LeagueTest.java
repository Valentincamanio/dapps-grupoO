package ar.edu.unq.desapp.futbolmarket.modelo.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.exception.UnsupportedLeagueException;
import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

class LeagueTest {

    @ParameterizedTest
    @EnumSource(League.class)
    void devuelveCadaLigaPorSuNombre(League league) {
        League found = League.fromName(league.name());

        assertThat(found).isEqualTo(league);
    }

    @Test
    void recortaLosEspaciosDeLosExtremos() {
        League found = League.fromName(" PREMIER ");

        assertThat(found).isEqualTo(League.PREMIER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"MLS", "premier", "   "})
    void rechazaUnaLigaQueNoEsUnaDeLasCinco(String value) {
        assertThatThrownBy(() -> League.fromName(value))
                .isInstanceOf(UnsupportedLeagueException.class)
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void elMensajeRepiteElValorRecibidoYListaLasLigasAdmitidas() {
        assertThatThrownBy(() -> League.fromName("MLS"))
                .hasMessage("La liga 'MLS' no es una de las admitidas: PREMIER, BUNDESLIGA, LA_LIGA, SERIE_A, LIGUE_1.");
    }
}
