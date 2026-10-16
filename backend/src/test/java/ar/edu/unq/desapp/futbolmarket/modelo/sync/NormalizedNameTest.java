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

    @Test
    void elApostrofoSeparaPalabrasQueLaFormaCompactaVuelveAJuntar() {
        NormalizedName normalized = NormalizedName.ofPlayer("N'Diaye");

        assertThat(normalized.words()).containsExactly("n", "diaye");
        assertThat(normalized.lastWord()).isEqualTo("diaye");
        assertThat(normalized.compact()).isEqualTo("ndiaye");
    }

    @Test
    void elGuionSeparaPalabras() {
        NormalizedName normalized = NormalizedName.ofPlayer("Lee Kang-In");

        assertThat(normalized.words()).containsExactly("lee", "kang", "in");
        assertThat(normalized.lastWord()).isEqualTo("in");
        assertThat(normalized.compact()).isEqualTo("leekangin");
    }

    @Test
    void unNombreVacioNoTienePalabrasNiApellidoNiFormaCompacta() {
        NormalizedName normalized = NormalizedName.ofPlayer(null);

        assertThat(normalized.words()).isEmpty();
        assertThat(normalized.lastWord()).isEmpty();
        assertThat(normalized.compact()).isEmpty();
    }

    @Test
    void unNombreCortoEstaDentroDelNombreCompletoConPalabrasEnterasOComienzos() {
        NormalizedName rafaGarrido = NormalizedName.ofPlayer("Rafa Garrido");
        NormalizedName diogoSousa = NormalizedName.ofPlayer("Diogo Sousa");
        NormalizedName manuHernando = NormalizedName.ofPlayer("Manu Hernando");

        boolean rafita = rafaGarrido.isWithin(NormalizedName.ofPlayer("Rafael Garrido Hierro"));
        boolean diogo = diogoSousa.isWithin(NormalizedName.ofPlayer("Diogo Lobão de Sousa"));
        boolean manu = manuHernando.isWithin(NormalizedName.ofPlayer("José Manuel Hernando Riol"));

        assertThat(rafita).isTrue();
        assertThat(diogo).isTrue();
        assertThat(manu).isTrue();
    }

    @Test
    void elSegundoApellidoSoloNoAlcanzaParaEstarDentro() {
        NormalizedName catalog = NormalizedName.ofPlayer("Pablo García");

        boolean within = catalog.isWithin(NormalizedName.ofPlayer("Mauro Echegoyen García"));

        assertThat(within).isFalse();
    }

    @Test
    void unaPalabraDeTresLetrasSoloValeEntera() {
        NormalizedName catalog = NormalizedName.ofPlayer("Edu");

        boolean within = catalog.isWithin(NormalizedName.ofPlayer("Eduardo Camavinga"));

        assertThat(within).isFalse();
    }

    @Test
    void unaPalabraDelNombreCompletoNoSeUsaDosVeces() {
        NormalizedName catalog = NormalizedName.ofPlayer("Garrido Garrido");

        boolean within = catalog.isWithin(NormalizedName.ofPlayer("Rafael Garrido"));

        assertThat(within).isFalse();
    }

    /**
     * Si "Rafa" tomara primero "Rafael", "Rafael" no encontraría ninguna palabra.
     */
    @Test
    void lasPalabrasIgualesSeUsanAntesQueLosComienzos() {
        NormalizedName catalog = NormalizedName.ofPlayer("Rafa Rafael");

        boolean within = catalog.isWithin(NormalizedName.ofPlayer("Rafael Rafat Hierro"));

        assertThat(within).isTrue();
    }

    @Test
    void dosNombresConElMismoNombreDePilaCompartenUnaPalabra() {
        NormalizedName fatawu = NormalizedName.ofPlayer("Abdul Fatawu");

        boolean shares = fatawu.sharesWordWith(NormalizedName.ofPlayer("Abdul Issahaku"));

        assertThat(shares).isTrue();
    }

    @Test
    void unaPalabraDeMenosDeCuatroLetrasNoCuentaComoPalabraEnComun() {
        NormalizedName tamari = NormalizedName.ofPlayer("Mousa Al Tamari");

        boolean shares = tamari.sharesWordWith(NormalizedName.ofPlayer("Musa Al Taamari"));

        assertThat(shares).isFalse();
    }

    @Test
    void dosNombresSinRelacionNoCompartenPalabras() {
        NormalizedName ciervo = NormalizedName.ofPlayer("Riccardo Ciervo");

        boolean shares = ciervo.sharesWordWith(NormalizedName.ofPlayer("Ignace Van Der Brempt"));

        assertThat(shares).isFalse();
    }

    @Test
    void unNombreVacioNoCompartePalabras() {
        NormalizedName empty = NormalizedName.ofPlayer(null);

        boolean shares = empty.sharesWordWith(NormalizedName.ofPlayer("Abdul Issahaku"));

        assertThat(shares).isFalse();
    }

    @Test
    void unNombreVacioNoEstaDentroDeNinguno() {
        NormalizedName empty = NormalizedName.ofPlayer(null);

        boolean within = empty.isWithin(NormalizedName.ofPlayer("Rafael Garrido Hierro"));

        assertThat(within).isFalse();
    }
}
