package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.FixtureRowDto;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extrae el array {@code fixtureMatches} de la página de fixtures de un equipo (research D4).
 *
 * <p>Es JavaScript, no JSON: usa comillas simples y deja posiciones vacías en los partidos sin
 * jugar. Se normaliza a JSON en un solo recorrido, se lee como filas de nodos y se toman las
 * posiciones del contrato (contracts/whoscored-pages.md). Sin el marcador, o con un array que no
 * se puede leer, la página es un rechazo.</p>
 */
@Component
public class FixtureMatchesExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixtureMatchesExtractor.class);
    private static final String MARKER = "fixtureMatches:";
    private static final String ARRAY_START = "[";
    private static final TypeReference<List<List<JsonNode>>> ROWS = new TypeReference<>() {
    };

    private static final int ID = 0;
    private static final int DATE = 2;
    private static final int HOME_TEAM_ID = 4;
    private static final int HOME_TEAM_NAME = 5;
    private static final int AWAY_TEAM_ID = 7;
    private static final int AWAY_TEAM_NAME = 8;
    private static final int STATUS = 14;
    private static final int TOURNAMENT_ID = 18;

    private final JsonMapper jsonMapper;

    public FixtureMatchesExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<List<FixtureRowDto>> extract(String html) {
        OptionalInt start = ScriptBlockReader.valueAfter(html, MARKER);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        return ScriptBlockReader.read(html, start.getAsInt())
                .filter(block -> block.startsWith(ARRAY_START))
                .map(JavaScriptArrays::toJson)
                .flatMap(this::parse);
    }

    private Optional<List<FixtureRowDto>> parse(String json) {
        try {
            List<List<JsonNode>> rows = jsonMapper.readValue(json, ROWS);
            return Optional.of(rows.stream().map(FixtureMatchesExtractor::toRow).toList());
        } catch (JacksonException e) {
            LOGGER.debug("El array fixtureMatches no se pudo leer: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }

    private static FixtureRowDto toRow(List<JsonNode> row) {
        Long tournamentId = longAt(row, TOURNAMENT_ID);
        return new FixtureRowDto(longAt(row, ID), textAt(row, DATE), longAt(row, HOME_TEAM_ID),
                textAt(row, HOME_TEAM_NAME), longAt(row, AWAY_TEAM_ID), textAt(row, AWAY_TEAM_NAME),
                textAt(row, STATUS), tournamentId == null ? null : tournamentId.intValue());
    }

    private static Long longAt(List<JsonNode> row, int position) {
        JsonNode node = nodeAt(row, position);
        return node != null && node.isNumber() ? node.longValue() : null;
    }

    private static String textAt(List<JsonNode> row, int position) {
        JsonNode node = nodeAt(row, position);
        return node == null ? null : node.asString();
    }

    /**
     * {@code null} si la fila es más corta o la posición estaba vacía.
     */
    private static JsonNode nodeAt(List<JsonNode> row, int position) {
        if (row == null || position >= row.size()) {
            return null;
        }
        JsonNode node = row.get(position);
        return node == null || node.isNull() ? null : node;
    }

    /**
     * Normaliza un array literal de JavaScript a JSON en un solo recorrido: cada string con comillas
     * simples pasa a comillas dobles ({@code \'} queda {@code '} y {@code "} pasa a {@code \"}), y
     * cada posición vacía ({@code [,}, {@code ,,} o {@code ,]}) pasa a {@code null}. Los strings con
     * comillas dobles se copian tal cual.
     */
    static final class JavaScriptArrays {
        private static final char SINGLE_QUOTE = '\'';
        private static final char DOUBLE_QUOTE = '"';
        private static final char ESCAPE = '\\';
        private static final String NULL = "null";

        private final String script;
        private final StringBuilder json;
        private int index;
        private char previous;

        private JavaScriptArrays(String script) {
            this.script = script;
            this.json = new StringBuilder(script.length());
        }

        static String toJson(String script) {
            JavaScriptArrays arrays = new JavaScriptArrays(script);
            arrays.convert();
            return arrays.json.toString();
        }

        private void convert() {
            while (index < script.length()) {
                char current = script.charAt(index);
                if (current == SINGLE_QUOTE) {
                    copySingleQuoted();
                } else if (current == DOUBLE_QUOTE) {
                    copyDoubleQuoted();
                } else {
                    copyStructural(current);
                }
                index++;
            }
        }

        private void copyStructural(char current) {
            boolean emptyBeforeComma = current == ',' && (previous == '[' || previous == ',');
            boolean emptyBeforeClose = current == ']' && previous == ',';
            if (emptyBeforeComma || emptyBeforeClose) {
                json.append(NULL);
            }
            json.append(current);
            if (!Character.isWhitespace(current)) {
                previous = current;
            }
        }

        private void copySingleQuoted() {
            json.append(DOUBLE_QUOTE);
            index++;
            while (index < script.length() && script.charAt(index) != SINGLE_QUOTE) {
                char current = script.charAt(index);
                if (current == ESCAPE && index + 1 < script.length()) {
                    index++;
                    appendEscaped(script.charAt(index));
                } else if (current == DOUBLE_QUOTE) {
                    json.append(ESCAPE).append(DOUBLE_QUOTE);
                } else {
                    json.append(current);
                }
                index++;
            }
            json.append(DOUBLE_QUOTE);
            previous = DOUBLE_QUOTE;
        }

        private void appendEscaped(char escaped) {
            if (escaped != SINGLE_QUOTE) {
                json.append(ESCAPE);
            }
            json.append(escaped);
        }

        private void copyDoubleQuoted() {
            json.append(DOUBLE_QUOTE);
            index++;
            while (index < script.length() && script.charAt(index) != DOUBLE_QUOTE) {
                char current = script.charAt(index);
                json.append(current);
                if (current == ESCAPE && index + 1 < script.length()) {
                    index++;
                    json.append(script.charAt(index));
                }
                index++;
            }
            json.append(DOUBLE_QUOTE);
            previous = DOUBLE_QUOTE;
        }
    }
}
