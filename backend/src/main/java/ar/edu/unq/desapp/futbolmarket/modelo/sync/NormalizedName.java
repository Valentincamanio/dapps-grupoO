package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Un nombre de equipo o de jugador listo para comparar entre Football-Data y WhoScored (research
 * D12): sin tildes, en minúsculas y con solo letras y dígitos separados por un espacio.
 *
 * <p>Las minúsculas van antes del reemplazo de las letras que NFD no descompone, así el reemplazo
 * cubre también las mayúsculas, como la {@code Ø} de "Ødegaard".</p>
 */
public record NormalizedName(String value) {
    /**
     * Palabras que distinguen la forma jurídica del club y no al club: "Everton FC" y "Everton"
     * son el mismo equipo.
     */
    public static final Set<String> CLUB_WORDS = Set.of(
            "fc", "afc", "cf", "sc", "ac", "as", "ss", "ssc", "us", "cd", "ud", "sd", "rc", "rcd", "ca", "sv", "fsv",
            "vfb", "vfl", "tsg", "bv", "1");

    private static final Map<String, String> UNDECOMPOSED_LETTERS = Map.of(
            "ø", "o", "æ", "ae", "œ", "oe", "ß", "ss", "đ", "d", "ð", "d", "ł", "l", "ı", "i", "þ", "th");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String SEPARATOR = " ";

    /**
     * Un nombre que falta queda vacío: no coincide con el de ningún jugador ni equipo del catálogo,
     * que siempre tienen nombre.
     */
    public static NormalizedName ofPlayer(String name) {
        return new NormalizedName(normalize(name));
    }

    public static NormalizedName ofTeam(String name) {
        String withoutClubWords = Arrays.stream(normalize(name).split(SEPARATOR))
                .filter(word -> !CLUB_WORDS.contains(word))
                .collect(Collectors.joining(SEPARATOR));
        return new NormalizedName(withoutClubWords);
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String withoutMarks = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        String lowerCase = withoutMarks.toLowerCase(Locale.ROOT);
        String replaced = replaceUndecomposedLetters(lowerCase);
        return NOT_LETTER_OR_DIGIT.matcher(replaced).replaceAll(SEPARATOR).trim();
    }

    private static String replaceUndecomposedLetters(String name) {
        StringBuilder replaced = new StringBuilder(name.length());
        name.codePoints().forEach(codePoint -> {
            String letter = Character.toString(codePoint);
            replaced.append(UNDECOMPOSED_LETTERS.getOrDefault(letter, letter));
        });
        return replaced.toString();
    }
}
