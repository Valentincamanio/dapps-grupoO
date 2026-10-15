package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Recorta un objeto o un array de JavaScript o JSON del HTML de una página, contando llaves y
 * corchetes y salteando los strings, con comillas simples o dobles y sus escapes (research D4).
 *
 * <p>Una expresión regular cortaría el bloque en el primer {@code }} que aparezca dentro de un
 * nombre.</p>
 */
public final class ScriptBlockReader {
    private static final char ESCAPE = '\\';
    private static final int ESCAPE_SEQUENCE_LENGTH = 2;
    private static final int NOT_FOUND = -1;

    private ScriptBlockReader() {
    }

    /**
     * Dónde empieza el valor que sigue a {@code marker}, salteando los espacios. Vacío si el
     * marcador no está.
     */
    public static OptionalInt valueAfter(String text, String marker) {
        int markerStart = text.indexOf(marker);
        if (markerStart == NOT_FOUND) {
            return OptionalInt.empty();
        }
        int index = markerStart + marker.length();
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return OptionalInt.of(index);
    }

    /**
     * El bloque que empieza en {@code from}, con su cierre incluido. Vacío si en esa posición no
     * empieza un objeto o un array, o si el bloque no cierra.
     */
    public static Optional<String> read(String text, int from) {
        if (from < 0 || from >= text.length() || !isOpening(text.charAt(from))) {
            return Optional.empty();
        }
        int depth = 0;
        int index = from;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (isQuote(current)) {
                index = endOfString(text, index);
            } else if (isOpening(current)) {
                depth++;
            } else if (isClosing(current)) {
                depth--;
                if (depth == 0) {
                    return Optional.of(text.substring(from, index + 1));
                }
            }
            index++;
        }
        return Optional.empty();
    }

    /**
     * La posición de la comilla que cierra el string que abre la de {@code start}, salteando los
     * caracteres escapados. Si no cierra, el final del texto.
     */
    private static int endOfString(String text, int start) {
        char quote = text.charAt(start);
        int index = start + 1;
        while (index < text.length() && text.charAt(index) != quote) {
            index += text.charAt(index) == ESCAPE ? ESCAPE_SEQUENCE_LENGTH : 1;
        }
        return index;
    }

    private static boolean isQuote(char character) {
        return character == '"' || character == '\'';
    }

    private static boolean isOpening(char character) {
        return character == '{' || character == '[';
    }

    private static boolean isClosing(char character) {
        return character == '}' || character == ']';
    }
}
