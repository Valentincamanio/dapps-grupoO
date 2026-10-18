package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class ScriptBlockReaderTest {

    @Test
    void recortaElObjetoQueEmpiezaEnLaPosicionYDejaAfueraLoQueSigue() {
        String text = "args = {\"a\":1,\"b\":2}, siguiente: {}";

        Optional<String> block = ScriptBlockReader.read(text, text.indexOf('{'));

        assertThat(block).contains("{\"a\":1,\"b\":2}");
    }

    @Test
    void ignoraLlavesYCorchetesDentroDeLosStrings() {
        String text = "{\"name\":\"Nombre con } llave y ] corchete\",\"list\":[1,2]} resto";

        Optional<String> block = ScriptBlockReader.read(text, 0);

        assertThat(block).contains("{\"name\":\"Nombre con } llave y ] corchete\",\"list\":[1,2]}");
    }

    @Test
    void respetaLasComillasEscapadasDentroDeUnString() {
        String text = "{\"name\":\"dice \\\"hola}\\\" y sigue\",\"slash\":\"\\\\\"} resto";

        Optional<String> block = ScriptBlockReader.read(text, 0);

        assertThat(block).contains("{\"name\":\"dice \\\"hola}\\\" y sigue\",\"slash\":\"\\\\\"}");
    }

    @Test
    void leeArraysConStringsDeComillasSimples() {
        String text = "[[1,'Nott\\'m Forest','a]b'],[2,'Everton',,]],\nprevious: []";

        Optional<String> block = ScriptBlockReader.read(text, 0);

        assertThat(block).contains("[[1,'Nott\\'m Forest','a]b'],[2,'Everton',,]]");
    }

    @Test
    void cierraEnElNivelDelPrincipioConBloquesAnidados() {
        String text = "{\"a\":{\"b\":[{\"c\":[1,{\"d\":2}]}]},\"e\":3}}}";

        Optional<String> block = ScriptBlockReader.read(text, 0);

        assertThat(block).contains("{\"a\":{\"b\":[{\"c\":[1,{\"d\":2}]}]},\"e\":3}");
    }

    @Test
    void unBloqueQueNoCierraNoSeRecorta() {
        String text = "{\"a\":[1,2},\"b\":\"sin cerrar";

        Optional<String> block = ScriptBlockReader.read(text, 0);

        assertThat(block).isEmpty();
    }

    @Test
    void sinUnObjetoOUnArrayEnLaPosicionNoHayBloque() {
        String text = "matchCentreData: null,";

        Optional<String> block = ScriptBlockReader.read(text, text.indexOf("null"));

        assertThat(block).isEmpty();
    }

    @Test
    void valueAfterSalteaLosEspaciosDespuesDelMarcador() {
        String text = "matchId:1983600,\n    matchCentreData:   \n {\"a\":1}";

        OptionalInt start = ScriptBlockReader.valueAfter(text, "matchCentreData:");

        assertThat(start).hasValue(text.indexOf('{'));
    }

    @Test
    void valueAfterSinElMarcadorEsVacio() {
        OptionalInt start = ScriptBlockReader.valueAfter("<html></html>", "matchCentreData:");

        assertThat(start).isEmpty();
    }
}
