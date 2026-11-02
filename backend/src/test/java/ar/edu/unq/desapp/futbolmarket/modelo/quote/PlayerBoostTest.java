package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidBoostException;

class PlayerBoostTest {
    private static final long PLAYER_ID = 7L;

    @ParameterizedTest
    @ValueSource(ints = {200, 225, 250, 300, 375, 400})
    void aceptaDeDoscientosACuatrocientosDeAVeinticinco(int percent) {
        var boost = new PlayerBoost(PLAYER_ID, percent);

        var multiplier = boost.multiplier();

        assertThat(multiplier).isEqualByComparingTo(String.valueOf(percent / 100.0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, 175, 199, 210, 425, 500})
    void rechazaUnPorcentajeFueraDeRangoOQueNoEsMultiploDeVeinticinco(int percent) {
        assertThatThrownBy(() -> new PlayerBoost(PLAYER_ID, percent))
                .isInstanceOf(InvalidBoostException.class)
                .hasMessage("El potenciado va de 200 % a 400 %, de a 25 %, y se pidió " + percent + " %.");
    }
}
