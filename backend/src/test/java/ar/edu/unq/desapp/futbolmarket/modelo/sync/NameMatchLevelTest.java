package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Los niveles de comparación de nombres de FR-019b, con los ejemplos reales de research D14: el
 * nombre de WhoScored primero y el de Football-Data después.
 */
class NameMatchLevelTest {

    @ParameterizedTest(name = "{0}: ''{1}'' y ''{2}''")
    @CsvSource(delimiter = '|', textBlock = """
            SAME_WORDS      | Lee Kang-In        | Kang-in Lee
            SAME_WORDS      | Djené Dakonam      | Dakonam Djené
            CONTAINED_WORDS | Antonio Sivera     | Sivera
            CONTAINED_WORDS | Ryan Giles         | Ryan John Giles
            SAME_SURNAME    | Álex Baena         | Alejandro Baena
            SAME_SURNAME    | Mathew Ryan        | Maty Ryan
            SAME_COMPACT    | Evan Ndicka        | Evan N'Dicka
            SAME_COMPACT    | Enrico Delprato    | Enrico Del Prato
            SAME_COMPACT    | Woo-yeong Jeong    | Jeong Wooyeong
            SAME_COMPACT    | Gavi               | Pablo Gavira
            SIMILAR_SURNAME | Yehor Yarmoliuk    | Yegor Yarmolyuk
            SIMILAR_SURNAME | Mousa Al Tamari    | Musa Al Taamari
            WORD_PREFIX     | Chupe              | Chupete
            WORD_PREFIX     | Kiko Femenía       | Femenía Far
            """)
    void elEjemploRealCoincideEnSuNivelEnLosDosSentidos(NameMatchLevel level, String reported, String catalog) {
        NormalizedName reportedName = NormalizedName.ofPlayer(reported);
        NormalizedName catalogName = NormalizedName.ofPlayer(catalog);

        boolean forward = level.matches(reportedName, catalogName);
        boolean backward = level.matches(catalogName, reportedName);

        assertThat(forward).isTrue();
        assertThat(backward).isTrue();
    }

    @ParameterizedTest(name = "{0}: ''{1}'' y ''{2}''")
    @CsvSource(delimiter = '|', textBlock = """
            SAME_WORDS      | Lee Kang-In        | Lee Jae-Sung
            SAME_WORDS      | Antonio Sivera     | Sivera
            CONTAINED_WORDS | Antonio Sivera     | Antonio Blanco
            SAME_SURNAME    | Álex Baena         | Álex Remiro
            SAME_COMPACT    | Evan Ndicka        | Evan Ferguson
            SAME_COMPACT    | Leo                | Leonardo Balerdi
            SIMILAR_SURNAME | Yehor Yarmoliuk    | Andriy Yarmolenko
            SIMILAR_SURNAME | Yéremy Pino        | Yeremi Pina
            WORD_PREFIX     | Gav                | Pablo Gavira
            WORD_PREFIX     | Chupe              | Chucky Lozano
            WORD_PREFIX     | Abdul Fatawu       | Abdul Issahaku
            WORD_PREFIX     | Mathis Saka        | Mathis Amougou
            """)
    void unNombreParecidoNoCoincideEnEseNivel(NameMatchLevel level, String reported, String catalog) {
        NormalizedName reportedName = NormalizedName.ofPlayer(reported);
        NormalizedName catalogName = NormalizedName.ofPlayer(catalog);

        boolean forward = level.matches(reportedName, catalogName);
        boolean backward = level.matches(catalogName, reportedName);

        assertThat(forward).isFalse();
        assertThat(backward).isFalse();
    }

    @ParameterizedTest
    @EnumSource(NameMatchLevel.class)
    void unNombreVacioNuncaCoincide(NameMatchLevel level) {
        NormalizedName empty = NormalizedName.ofPlayer(null);
        NormalizedName name = NormalizedName.ofPlayer("Antonio Sivera");

        boolean forward = level.matches(empty, name);
        boolean backward = level.matches(name, empty);
        boolean both = level.matches(empty, empty);

        assertThat(forward).isFalse();
        assertThat(backward).isFalse();
        assertThat(both).isFalse();
    }

    @Test
    void tresCambiosEnElApellidoYaNoSonParecidos() {
        NormalizedName reported = NormalizedName.ofPlayer("Juan Martinez");
        NormalizedName catalog = NormalizedName.ofPlayer("Juan Martinoli");

        boolean matches = NameMatchLevel.SIMILAR_SURNAME.matches(reported, catalog);

        assertThat(matches).isFalse();
    }

    @Test
    void losNivelesVanDelMasSeguroAlMenosSeguro() {
        NameMatchLevel[] levels = NameMatchLevel.values();

        assertThat(Arrays.asList(levels)).containsExactly(NameMatchLevel.SAME_WORDS, NameMatchLevel.CONTAINED_WORDS,
                NameMatchLevel.SAME_SURNAME, NameMatchLevel.SAME_COMPACT, NameMatchLevel.SIMILAR_SURNAME,
                NameMatchLevel.WORD_PREFIX);
    }
}
