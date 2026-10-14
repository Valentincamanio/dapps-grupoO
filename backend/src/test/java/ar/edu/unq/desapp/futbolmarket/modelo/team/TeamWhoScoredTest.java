package ar.edu.unq.desapp.futbolmarket.modelo.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

class TeamWhoScoredTest {

    private static final String EVERTON_ID = "62";
    private static final String EVERTON_NAME = "Everton FC";
    private static final String EVERTON_CREST = "https://crests.football-data.org/62.png";
    private static final String EVERTON_WHOSCORED_ID = "31";
    private static final String OTHER_WHOSCORED_ID = "32";

    @Test
    void recortaElIdentificadorDeWhoScored() {
        Team team = new Team(1L, EVERTON_ID, EVERTON_NAME, null, League.PREMIER, "  31 ");

        assertThat(team.whoscoredId()).isEqualTo(EVERTON_WHOSCORED_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unIdentificadorDeWhoScoredEnBlancoQuedaEnNull(String whoscoredId) {
        Team team = new Team(1L, EVERTON_ID, EVERTON_NAME, null, League.PREMIER, whoscoredId);

        assertThat(team.whoscoredId()).isNull();
        assertThat(team.isCrossed()).isFalse();
    }

    @Test
    void losConstructoresDeConvenienciaDejanAlEquipoSinCruzar() {
        Team withId = new Team(1L, EVERTON_ID, EVERTON_NAME, null, League.PREMIER);
        Team withoutId = new Team(EVERTON_ID, EVERTON_NAME, null, League.PREMIER);

        assertThat(withId.whoscoredId()).isNull();
        assertThat(withoutId.whoscoredId()).isNull();
    }

    @Test
    void unEquipoConIdentificadorDeWhoScoredEstaCruzado() {
        Team team = crossedEverton();

        assertThat(team.isCrossed()).isTrue();
    }

    @Test
    void updateFromConservaElIdentificadorDeWhoScored() {
        Team team = crossedEverton();
        TeamSnapshot snapshot = new TeamSnapshot(EVERTON_ID, EVERTON_NAME, EVERTON_CREST, List.of());

        Team updated = team.updateFrom(snapshot, League.PREMIER);

        assertThat(updated)
                .isEqualTo(new Team(1L, EVERTON_ID, EVERTON_NAME, EVERTON_CREST, League.PREMIER, EVERTON_WHOSCORED_ID));
    }

    @Test
    void crossWithLeAsignaElIdentificadorDeWhoScoredSinCambiarLoDemas() {
        Team team = new Team(1L, EVERTON_ID, EVERTON_NAME, EVERTON_CREST, League.PREMIER);

        Team crossed = team.crossWith(EVERTON_WHOSCORED_ID);

        assertThat(crossed)
                .isEqualTo(new Team(1L, EVERTON_ID, EVERTON_NAME, EVERTON_CREST, League.PREMIER, EVERTON_WHOSCORED_ID));
    }

    @Test
    void crossWithConElMismoIdentificadorDevuelveUnEquipoIgual() {
        Team team = crossedEverton();

        Team crossedAgain = team.crossWith(EVERTON_WHOSCORED_ID);

        assertThat(crossedAgain).isEqualTo(team);
    }

    @Test
    void crossWithConOtroIdentificadorRechazaElCambioDeCruce() {
        Team team = crossedEverton();

        assertThatThrownBy(() -> team.crossWith(OTHER_WHOSCORED_ID))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El equipo ya está cruzado con otro identificador de WhoScored.");
    }

    private static Team crossedEverton() {
        return new Team(1L, EVERTON_ID, "Everton", null, League.PREMIER, EVERTON_WHOSCORED_ID);
    }
}
