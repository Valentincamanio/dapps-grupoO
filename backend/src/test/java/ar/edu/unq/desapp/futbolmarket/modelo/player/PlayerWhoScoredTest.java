package ar.edu.unq.desapp.futbolmarket.modelo.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class PlayerWhoScoredTest {

    private static final Team EVERTON = new Team(1L, "62", "Everton FC", null, League.PREMIER, "31");
    private static final Team TOTTENHAM = new Team(2L, "73", "Tottenham Hotspur FC", null, League.PREMIER, "30");
    private static final String PICKFORD_ID = "3204";
    private static final String PICKFORD_NAME = "Jordan Pickford";
    private static final LocalDate PICKFORD_BIRTH = LocalDate.of(1994, 3, 7);
    private static final String PICKFORD_NATIONALITY = "England";
    private static final String PICKFORD_WHOSCORED_ID = "93145";
    private static final String OTHER_WHOSCORED_ID = "93146";

    @Test
    void recortaElIdentificadorDeWhoScored() {
        Player player = pickford(" 93145  ");

        assertThat(player.whoscoredId()).isEqualTo(PICKFORD_WHOSCORED_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unIdentificadorDeWhoScoredEnBlancoQuedaEnNull(String whoscoredId) {
        Player player = pickford(whoscoredId);

        assertThat(player.whoscoredId()).isNull();
        assertThat(player.isCrossed()).isFalse();
    }

    @Test
    void losConstructoresDeConvenienciaDejanAlJugadorSinCruzar() {
        Player withEightArguments = new Player(
                1L, PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, EVERTON, PICKFORD_BIRTH, PICKFORD_NATIONALITY, true);
        Player withFiveArguments = new Player(1L, PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, EVERTON);
        Player withFourArguments = new Player(PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, EVERTON);

        assertThat(withEightArguments.whoscoredId()).isNull();
        assertThat(withFiveArguments.whoscoredId()).isNull();
        assertThat(withFourArguments.whoscoredId()).isNull();
    }

    @Test
    void unJugadorConIdentificadorDeWhoScoredEstaCruzado() {
        Player player = pickford(PICKFORD_WHOSCORED_ID);

        assertThat(player.isCrossed()).isTrue();
    }

    @Test
    void updateFromConservaElIdentificadorDeWhoScored() {
        Player player = pickford(PICKFORD_WHOSCORED_ID);
        PlayerSnapshot snapshot = new PlayerSnapshot(
                PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, PICKFORD_BIRTH, PICKFORD_NATIONALITY);

        Player updated = player.updateFrom(snapshot, TOTTENHAM);

        assertThat(updated.team()).isEqualTo(TOTTENHAM);
        assertThat(updated.whoscoredId()).isEqualTo(PICKFORD_WHOSCORED_ID);
    }

    @Test
    void deactivateConservaElIdentificadorDeWhoScored() {
        Player player = pickford(PICKFORD_WHOSCORED_ID);

        Player inactive = player.deactivate();

        assertThat(inactive.active()).isFalse();
        assertThat(inactive.whoscoredId()).isEqualTo(PICKFORD_WHOSCORED_ID);
    }

    @Test
    void crossWithLeAsignaElIdentificadorDeWhoScoredSinCambiarLoDemas() {
        Player player = pickford(null);

        Player crossed = player.crossWith(PICKFORD_WHOSCORED_ID);

        assertThat(crossed).isEqualTo(pickford(PICKFORD_WHOSCORED_ID));
    }

    @Test
    void crossWithConElMismoIdentificadorDevuelveUnJugadorIgual() {
        Player player = pickford(PICKFORD_WHOSCORED_ID);

        Player crossedAgain = player.crossWith(PICKFORD_WHOSCORED_ID);

        assertThat(crossedAgain).isEqualTo(player);
    }

    @Test
    void crossWithConOtroIdentificadorRechazaElCambioDeCruce() {
        Player player = pickford(PICKFORD_WHOSCORED_ID);

        assertThatThrownBy(() -> player.crossWith(OTHER_WHOSCORED_ID))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El jugador ya está cruzado con otro identificador de WhoScored.");
    }

    @Test
    void elDiaAnteriorAlCumpleanosTodaviaNoCumplioLosAnios() {
        Player player = pickford(null);

        OptionalInt age = player.ageOn(LocalDate.of(2026, 3, 6));

        assertThat(age).hasValue(31);
    }

    @Test
    void elDiaDelCumpleanosYaCumplioLosAnios() {
        Player player = pickford(null);

        OptionalInt age = player.ageOn(LocalDate.of(2026, 3, 7));

        assertThat(age).hasValue(32);
    }

    @Test
    void sinFechaDeNacimientoNoTieneEdad() {
        Player player = new Player(1L, PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, EVERTON);

        OptionalInt age = player.ageOn(LocalDate.of(2026, 3, 7));

        assertThat(age).isEmpty();
    }

    private static Player pickford(String whoscoredId) {
        return new Player(1L, PICKFORD_ID, PICKFORD_NAME, Position.GOALKEEPER, EVERTON, PICKFORD_BIRTH,
                PICKFORD_NATIONALITY, true, whoscoredId);
    }
}
