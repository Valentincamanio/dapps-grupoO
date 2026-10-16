package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentFixturesDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extrae el JSON {@code tournamentfixtures} de la página de una liga (research D4): el contenido del
 * {@code <script data-hypernova-key="tournamentfixtures">}, sin el {@code <!--} del principio ni el
 * {@code -->} del final. Sin el script, o con un JSON que no se puede leer, la página es un rechazo.
 *
 * <p>La misma clave aparece antes en el {@code <div>} que el servidor ya dibujó con esos datos, así
 * que se toma la primera aparición que está dentro de una etiqueta {@code <script>}.</p>
 */
@Component
public class TournamentFixturesExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TournamentFixturesExtractor.class);
    private static final String SCRIPT_MARKER = "data-hypernova-key=\"tournamentfixtures\"";
    private static final char TAG_START = '<';
    private static final String SCRIPT_START = "<script";
    private static final String TAG_END = ">";
    private static final String SCRIPT_END = "</script>";
    private static final String COMMENT_START = "<!--";
    private static final String COMMENT_END = "-->";
    private static final int NOT_FOUND = -1;

    private final JsonMapper jsonMapper;

    public TournamentFixturesExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<TournamentFixturesDto> extract(String html) {
        return scriptContent(html).map(TournamentFixturesExtractor::withoutComment).flatMap(this::parse);
    }

    private static Optional<String> scriptContent(String html) {
        int marker = scriptMarker(html);
        int tagEnd = marker == NOT_FOUND ? NOT_FOUND : html.indexOf(TAG_END, marker);
        int scriptEnd = tagEnd == NOT_FOUND ? NOT_FOUND : html.indexOf(SCRIPT_END, tagEnd);
        if (scriptEnd == NOT_FOUND) {
            return Optional.empty();
        }
        return Optional.of(html.substring(tagEnd + TAG_END.length(), scriptEnd).strip());
    }

    /**
     * La posición de la clave dentro de la primera etiqueta {@code <script>} que la tiene, salteando
     * las de otras etiquetas. {@code NOT_FOUND} si ningún script la tiene.
     */
    private static int scriptMarker(String html) {
        int marker = html.indexOf(SCRIPT_MARKER);
        while (marker != NOT_FOUND && !html.startsWith(SCRIPT_START, html.lastIndexOf(TAG_START, marker))) {
            marker = html.indexOf(SCRIPT_MARKER, marker + SCRIPT_MARKER.length());
        }
        return marker;
    }

    private static String withoutComment(String content) {
        String json = content;
        if (json.startsWith(COMMENT_START)) {
            json = json.substring(COMMENT_START.length());
        }
        if (json.endsWith(COMMENT_END)) {
            json = json.substring(0, json.length() - COMMENT_END.length());
        }
        return json;
    }

    private Optional<TournamentFixturesDto> parse(String json) {
        try {
            return Optional.of(jsonMapper.readValue(json, TournamentFixturesDto.class));
        } catch (JacksonException e) {
            LOGGER.debug("El JSON tournamentfixtures no se pudo leer: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }
}
