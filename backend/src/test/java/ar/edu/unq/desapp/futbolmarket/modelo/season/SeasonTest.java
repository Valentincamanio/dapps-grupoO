package ar.edu.unq.desapp.futbolmarket.modelo.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

class SeasonTest {

    private static final String SEASON_ID = "2502";
    private static final LocalDate START = LocalDate.of(2026, 8, 21);
    private static final LocalDate END = LocalDate.of(2027, 5, 30);
    private static final int CURRENT_MATCHDAY = 6;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnaTemporadaSinIdentificadorExterno(String externalId) {
        assertThatThrownBy(() -> new Season(null, externalId, League.PREMIER, START, END, CURRENT_MATCHDAY))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El identificador externo de la temporada es obligatorio.");
    }

    @Test
    void rechazaUnaTemporadaSinLiga() {
        assertThatThrownBy(() -> new Season(null, SEASON_ID, null, START, END, CURRENT_MATCHDAY))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La liga de la temporada es obligatoria.");
    }

    @Test
    void rechazaUnaTemporadaSinFechaDeInicio() {
        assertThatThrownBy(() -> new Season(null, SEASON_ID, League.PREMIER, null, END, CURRENT_MATCHDAY))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La fecha de inicio de la temporada es obligatoria.");
    }

    @Test
    void rechazaUnaTemporadaSinFechaDeFin() {
        assertThatThrownBy(() -> new Season(null, SEASON_ID, League.PREMIER, START, null, CURRENT_MATCHDAY))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La fecha de fin de la temporada es obligatoria.");
    }

    @Test
    void rechazaUnaTemporadaQueTerminaAntesDeEmpezar() {
        LocalDate dayBeforeStart = START.minusDays(1);

        assertThatThrownBy(() -> new Season(null, SEASON_ID, League.PREMIER, START, dayBeforeStart, CURRENT_MATCHDAY))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La fecha de fin de la temporada no puede ser anterior a la de inicio.");
    }

    @Test
    void aceptaUnaTemporadaQueEmpiezaYTerminaElMismoDia() {
        Season season = new Season(null, SEASON_ID, League.PREMIER, START, START, CURRENT_MATCHDAY);

        assertThat(season.endDate()).isEqualTo(season.startDate());
    }

    @Test
    void rechazaLaJornadaCero() {
        assertThatThrownBy(() -> new Season(null, SEASON_ID, League.PREMIER, START, END, 0))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La jornada actual de la temporada tiene que ser mayor o igual a 1.");
    }

    @Test
    void aceptaUnaTemporadaSinJornadaActual() {
        Season season = new Season(null, SEASON_ID, League.PREMIER, START, END, null);

        assertThat(season.currentMatchday()).isNull();
    }

    @Test
    void updateFromConservaLaIdentidadYLaLigaYTomaLasFechasYLaJornada() {
        Season season = new Season(3L, SEASON_ID, League.PREMIER, START, END, CURRENT_MATCHDAY);
        Season reported = new Season(null, SEASON_ID, League.LA_LIGA, START.plusDays(1), END.plusDays(7), 7);

        Season updated = season.updateFrom(reported);

        assertThat(updated).isEqualTo(
                new Season(3L, SEASON_ID, League.PREMIER, START.plusDays(1), END.plusDays(7), 7));
    }
}
