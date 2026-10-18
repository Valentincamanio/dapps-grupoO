package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Los niveles de comparación de nombres de jugadores de FR-019b, del más seguro al menos seguro
 * (research D14). Se prueban en este orden solo cuando el nombre exacto no encontró a nadie, y
 * siempre dentro del equipo y con la misma edad: acá solo se comparan los nombres.
 *
 * <p>Cada nivel es simétrico: da lo mismo cuál es el nombre de WhoScored y cuál el del catálogo.
 * Un nombre vacío nunca coincide.</p>
 */
public enum NameMatchLevel {
    /**
     * Las mismas palabras, en otro orden: "Lee Kang-In" y "Kang-in Lee".
     */
    SAME_WORDS {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            return Set.copyOf(first.words()).equals(Set.copyOf(second.words()));
        }
    },
    /**
     * Todas las palabras de un nombre están en el otro: "Antonio Sivera" y "Sivera".
     */
    CONTAINED_WORDS {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            return Set.copyOf(first.words()).containsAll(second.words());
        }
    },
    /**
     * La misma última palabra, que se toma como apellido: "Álex Baena" y "Alejandro Baena".
     */
    SAME_SURNAME {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            return first.lastWord().equals(second.lastWord());
        }
    },
    /**
     * El mismo nombre sin separadores en algún orden de sus palabras ("Ndicka" y "N'Dicka",
     * "Woo-yeong Jeong" y "Jeong Wooyeong"), o uno contenido en el otro así escrito, si tiene al
     * menos {@link #WORD_PREFIX_MIN_LENGTH} letras ("Gavi" y "Pablo Gavira").
     */
    SAME_COMPACT {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            String compact = second.compact();
            return compactForms(first).contains(compact)
                    || (compact.length() >= WORD_PREFIX_MIN_LENGTH && first.compact().contains(compact));
        }
    },
    /**
     * Apellidos de al menos {@link #SIMILAR_SURNAME_MIN_LENGTH} letras con a lo sumo
     * {@link #SIMILAR_SURNAME_MAX_CHANGES} cambios: "Yarmoliuk" y "Yarmolyuk".
     */
    SIMILAR_SURNAME {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            String surname = first.lastWord();
            String other = second.lastWord();
            return surname.length() >= SIMILAR_SURNAME_MIN_LENGTH && other.length() >= SIMILAR_SURNAME_MIN_LENGTH
                    && changesBetween(surname, other) <= SIMILAR_SURNAME_MAX_CHANGES;
        }
    },
    /**
     * Una palabra de un nombre es el comienzo de una del otro, las dos de al menos
     * {@link #WORD_PREFIX_MIN_LENGTH} letras: "Chupe" y "Chupete", "Kiko Femenía" y "Femenía Far".
     * Si los dos nombres tienen más de una palabra, la primera de cada uno con la primera del otro
     * no cuenta: el nombre de pila solo no alcanza ("Mathis Saka" y "Mathis Amougou").
     */
    WORD_PREFIX {
        @Override
        boolean contains(NormalizedName first, NormalizedName second) {
            List<String> words = first.words();
            List<String> prefixes = second.words();
            boolean givenNames = words.size() > 1 && prefixes.size() > 1;
            return IntStream.range(0, words.size()).anyMatch(wordIndex -> IntStream.range(0, prefixes.size())
                    .filter(prefixIndex -> !givenNames || wordIndex > 0 || prefixIndex > 0)
                    .anyMatch(prefixIndex -> startsWithLongPrefix(words.get(wordIndex), prefixes.get(prefixIndex))));
        }
    };

    public static final int SIMILAR_SURNAME_MIN_LENGTH = 5;
    public static final int SIMILAR_SURNAME_MAX_CHANGES = 2;
    public static final int WORD_PREFIX_MIN_LENGTH = 4;

    /**
     * Con más palabras, la forma compacta se compara solo en el orden original: las permutaciones
     * crecen como el factorial.
     */
    private static final int MAX_PERMUTED_WORDS = 4;

    /**
     * Si los dos nombres coinciden en este nivel, en cualquier orden.
     */
    public boolean matches(NormalizedName reported, NormalizedName catalog) {
        if (reported.value().isEmpty() || catalog.value().isEmpty()) {
            return false;
        }
        return contains(reported, catalog) || contains(catalog, reported);
    }

    /**
     * La regla del nivel en un sentido; {@link #matches} la prueba en los dos.
     */
    abstract boolean contains(NormalizedName first, NormalizedName second);

    private static boolean startsWithLongPrefix(String word, String prefix) {
        return word.length() >= WORD_PREFIX_MIN_LENGTH && prefix.length() >= WORD_PREFIX_MIN_LENGTH
                && word.startsWith(prefix);
    }

    /**
     * Las formas compactas del nombre en cada orden de sus palabras.
     */
    private static Set<String> compactForms(NormalizedName name) {
        List<String> words = name.words();
        if (words.size() > MAX_PERMUTED_WORDS) {
            return Set.of(name.compact());
        }
        Set<String> forms = new HashSet<>();
        permute(new ArrayList<>(words), 0, forms);
        return forms;
    }

    private static void permute(List<String> words, int from, Set<String> forms) {
        if (from == words.size()) {
            forms.add(String.join("", words));
            return;
        }
        for (int index = from; index < words.size(); index++) {
            Collections.swap(words, from, index);
            permute(words, from + 1, forms);
            Collections.swap(words, from, index);
        }
    }

    /**
     * La distancia de Levenshtein: cuántas letras hay que agregar, quitar o cambiar para pasar de
     * una palabra a la otra.
     */
    private static int changesBetween(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                int substitution = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }
}
