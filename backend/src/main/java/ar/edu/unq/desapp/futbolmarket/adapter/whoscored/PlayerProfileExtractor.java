package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.PlayerProfileDto;

/**
 * Lee la fecha de nacimiento y el nombre completo de la página de un jugador (research D28;
 * contracts/whoscored-pages.md, "Página de un jugador"): vienen en el HTML del servidor como
 * {@code <span class="info-label">Age: </span>20 years old (<i>20-01-2006</i>)} y
 * {@code <span class="info-label">Full Name: </span>Rafael Garrido Hierro}, hasta el {@code </div>}.
 *
 * <p>Una página sin ningún {@code info-label} no es la del jugador y es un rechazo. Una con los datos
 * del jugador pero sin la edad, con una fecha que no se puede leer o sin el nombre completo, no lo
 * es: al jugador le falta ese dato.</p>
 */
@Component
public class PlayerProfileExtractor {
    private static final String PROFILE_MARKER = "class=\"info-label\"";
    private static final String AGE_LABEL = ">Age:";
    private static final String DATE_START = "<i>";
    private static final String DATE_END = "</i>";
    private static final String FULL_NAME_LABEL = ">Full Name:";
    private static final String LABEL_END = "</span>";
    private static final String BLOCK_END = "</div>";
    private static final int MAX_DISTANCE_FROM_LABEL = 200;
    private static final int NOT_FOUND = -1;
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    public Optional<PlayerProfileDto> extract(String html) {
        if (!html.contains(PROFILE_MARKER)) {
            return Optional.empty();
        }
        return Optional.of(new PlayerProfileDto(birthDate(html), fullName(html)));
    }

    /**
     * La fecha del {@code <i>} que sigue a la etiqueta de la edad, o {@code null}.
     */
    private static LocalDate birthDate(String html) {
        int label = html.indexOf(AGE_LABEL);
        int start = label == NOT_FOUND ? NOT_FOUND : html.indexOf(DATE_START, label);
        if (start == NOT_FOUND || start - label > MAX_DISTANCE_FROM_LABEL) {
            return null;
        }
        int end = html.indexOf(DATE_END, start);
        if (end == NOT_FOUND) {
            return null;
        }
        try {
            return LocalDate.parse(html.substring(start + DATE_START.length(), end).strip(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * El texto entre la etiqueta del nombre completo y el fin de su bloque, sin los espacios de los
     * bordes, con los seguidos reducidos a uno y las entidades decodificadas, o {@code null}.
     */
    private static String fullName(String html) {
        int label = html.indexOf(FULL_NAME_LABEL);
        int start = label == NOT_FOUND ? NOT_FOUND : html.indexOf(LABEL_END, label);
        int end = start == NOT_FOUND ? NOT_FOUND : html.indexOf(BLOCK_END, start);
        if (end == NOT_FOUND || end - label > MAX_DISTANCE_FROM_LABEL) {
            return null;
        }
        String text = HtmlUtils.htmlUnescape(html.substring(start + LABEL_END.length(), end));
        String fullName = SPACES.matcher(text).replaceAll(" ").strip();
        return fullName.isEmpty() ? null : fullName;
    }
}
