package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NormalizedNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Thomas Müller         | thomas muller
            Lisandro Martínez     | lisandro martinez
            Martin Ødegaard       | martin odegaard
            Leon Groß             | leon gross
            Łukasz Fabiański      | lukasz fabianski
            Altay Bayındır        | altay bayindir
            KAI HAVERTZ           | kai havertz
            Dominic Calvert-Lewin | dominic calvert lewin
            Jake O'Brien          | jake o brien
            '  Beto  '            | beto
            """)
    void quitaTildesYMayusculasYDejaSoloLetrasYDigitosSeparadosPorUnEspacio(String name, String expected) {
        NormalizedName normalized = NormalizedName.ofPlayer(name);

        assertThat(normalized.value()).isEqualTo(expected);
    }

    @Test
    void elNombreDeWhoScoredSinTildesYElDelCatalogoConTildesSonIguales() {
        NormalizedName catalog = NormalizedName.ofPlayer("Martin Ødegaard");
        NormalizedName source = NormalizedName.ofPlayer("Martin Odegaard");

        boolean equal = catalog.equals(source);

        assertThat(equal).isTrue();
    }

    @Test
    void sinLaPalabraDeClubElEquipoEsElMismo() {
        NormalizedName catalog = NormalizedName.ofTeam("Everton FC");
        NormalizedName source = NormalizedName.ofTeam("Everton");

        boolean equal = catalog.equals(source);

        assertThat(equal).isTrue();
        assertThat(catalog.value()).isEqualTo("everton");
    }

    @Test
    void unaPalabraQueNoEsDeClubDistingueAlEquipo() {
        NormalizedName catalog = NormalizedName.ofTeam("Hull City AFC");
        NormalizedName source = NormalizedName.ofTeam("Hull");

        boolean equal = catalog.equals(source);

        assertThat(equal).isFalse();
        assertThat(catalog.value()).isEqualTo("hull city");
    }

    @Test
    void elNombreDeUnJugadorConservaLasPalabrasDeClub() {
        NormalizedName normalized = NormalizedName.ofPlayer("Everton FC");

        assertThat(normalized.value()).isEqualTo("everton fc");
    }

    @Test
    void unNombreQueFaltaQuedaVacio() {
        NormalizedName normalized = NormalizedName.ofPlayer(null);

        assertThat(normalized.value()).isEmpty();
    }
}
