package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.util.Optional;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchCentreDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extrae el objeto {@code matchCentreData} de la página de un partido (research D4).
 *
 * <p>Distingue tres casos: un vacío por fuera es un rechazo (el marcador falta o el JSON no se puede
 * leer); un vacío por dentro es una página válida de un partido que todavía no tiene datos
 * ({@code matchCentreData: null}); y el objeto leído.</p>
 */
@Component
public class MatchCentreExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchCentreExtractor.class);
    private static final String MARKER = "matchCentreData:";
    private static final String NO_DATA = "null";

    private final JsonMapper jsonMapper;

    public MatchCentreExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<Optional<MatchCentreDto>> extract(String html) {
        OptionalInt start = ScriptBlockReader.valueAfter(html, MARKER);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        if (html.startsWith(NO_DATA, start.getAsInt())) {
            return Optional.of(Optional.empty());
        }
        return ScriptBlockReader.read(html, start.getAsInt()).flatMap(this::parse).map(Optional::of);
    }

    private Optional<MatchCentreDto> parse(String json) {
        try {
            return Optional.of(jsonMapper.readValue(json, MatchCentreDto.class));
        } catch (JacksonException e) {
            LOGGER.debug("El bloque matchCentreData no se pudo leer: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }
}
