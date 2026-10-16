package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Las palabras del nombre, en orden: {@code "n diaye"} da {@code n} y {@code diaye}. Un nombre
     * vacío no tiene ninguna. Las usan los niveles de cruce de jugadores (research D14).
     */
    public List<String> words() {
        if (value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split(SEPARATOR));
    }

    /**
     * La última palabra, que para un jugador se toma como su apellido. Vacía si el nombre lo es.
     */
    public String lastWord() {
        List<String> words = words();
        return words.isEmpty() ? "" : words.getLast();
    }

    /**
     * El nombre sin separadores: {@code "n diaye"} da {@code ndiaye}.
     */
    public String compact() {
        return value.replace(SEPARATOR, "");
    }

    /**
     * Si este nombre está dentro de un nombre completo (FR-019d): cada una de sus palabras es una
     * palabra distinta de {@code fullName}, o el comienzo de una si tiene al menos
     * {@link NameMatchLevel#WORD_PREFIX_MIN_LENGTH} letras. Primero se buscan las palabras iguales y
     * después las que empiezan con las que faltan, siempre entre las que no usó otra palabra:
     * {@code "rafa garrido"} está dentro de {@code "rafael garrido hierro"} y {@code "pablo garcia"}
     * no está dentro de {@code "mauro echegoyen garcia"}. Un nombre vacío nunca está dentro.
     */
    public boolean isWithin(NormalizedName fullName) {
        List<String> words = words();
        if (words.isEmpty()) {
            return false;
        }
        List<String> available = new ArrayList<>(fullName.words());
        List<String> withoutEqualWord = words.stream().filter(word -> !available.remove(word)).toList();
        return withoutEqualWord.stream().allMatch(word -> removeStartingWith(available, word));
    }

    /**
     * Si los dos nombres tienen una palabra igual de al menos
     * {@link NameMatchLevel#WORD_PREFIX_MIN_LENGTH} letras: {@code "abdul fatawu"} y
     * {@code "abdul issahaku"} sí, y {@code "mousa al tamari"} y {@code "musa al taamari"} no,
     * porque {@code al} es corta. Es una de las relaciones de nombre del paso 1 de FR-019d.
     */
    public boolean sharesWordWith(NormalizedName other) {
        List<String> otherWords = other.words();
        return words().stream()
                .filter(word -> word.length() >= NameMatchLevel.WORD_PREFIX_MIN_LENGTH)
                .anyMatch(otherWords::contains);
    }

    private static boolean removeStartingWith(List<String> available, String start) {
        if (start.length() < NameMatchLevel.WORD_PREFIX_MIN_LENGTH) {
            return false;
        }
        Optional<String> word = available.stream().filter(candidate -> candidate.startsWith(start)).findFirst();
        word.ifPresent(available::remove);
        return word.isPresent();
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
