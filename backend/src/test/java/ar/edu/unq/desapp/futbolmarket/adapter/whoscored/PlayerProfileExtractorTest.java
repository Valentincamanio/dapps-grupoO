package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.PlayerProfileDto;

/**
 * La fecha de nacimiento de la página de un jugador (research D28), con el bloque verificado el
 * 2026-10-15 en la página de Diogo Sousa.
 */
class PlayerProfileExtractorTest {

    private static final String PROFILE_WITHOUT_AGE = "<div><span class=\"info-label\">Name: </span>Rafita</div>"
            + "<div><span class=\"info-label\">Current Team: </span>Málaga</div>";

    private final PlayerProfileExtractor extractor = new PlayerProfileExtractor();

    @Test
    void leeLaFechaDeNacimientoQueSigueALaEdad() throws IOException {
        String html = page("player-profile.html");

        Optional<PlayerProfileDto> profile = extractor.extract(html);

        assertThat(profile).contains(new PlayerProfileDto(LocalDate.of(2006, 1, 20), "Diogo Lobão de Sousa"));
    }

    @Test
    void unaPaginaDeJugadorSinLaEdadNoEsUnRechazoPeroNoTraeFecha() {
        Optional<PlayerProfileDto> profile = extractor.extract(PROFILE_WITHOUT_AGE);

        assertThat(profile).contains(new PlayerProfileDto(null, null));
    }

    @Test
    void unaFechaQueNoExisteSeTomaComoSinFecha() {
        String html = "<span class=\"info-label\">Age: </span>20 years old (<i>31-02-2006</i>)";

        Optional<PlayerProfileDto> profile = extractor.extract(html);

        assertThat(profile).contains(new PlayerProfileDto(null, null));
    }

    /**
     * Como la del Vitinha del Genoa, verificada el 2026-10-15: pasa de {@code Name} a
     * {@code Current Team}.
     */
    @Test
    void unaPaginaSinNombreCompletoTraeLaFechaYNingunNombre() {
        String html = "<div><span class=\"info-label\">Name: </span>Vitinha</div>"
                + "<div><span class=\"info-label\">Current Team: </span>Genoa</div>"
                + "<div><span class=\"info-label\">Age: </span>26 years old (<i>15-03-2000</i>)</div>";

        Optional<PlayerProfileDto> profile = extractor.extract(html);

        assertThat(profile).contains(new PlayerProfileDto(LocalDate.of(2000, 3, 15), null));
    }

    @Test
    void elNombreCompletoDecodificaLasEntidadesYReduceLosEspacios() {
        String html = "<div>\r\n    <span class=\"info-label\">Full Name: </span>Lamine  N&#39;Diaye\r\n"
                + "        </div>";

        Optional<PlayerProfileDto> profile = extractor.extract(html);

        assertThat(profile.map(PlayerProfileDto::fullName)).contains("Lamine N'Diaye");
    }

    @Test
    void sinLosDatosDelJugadorEsUnRechazo() throws IOException {
        String html = page("rejected-403.html");

        Optional<PlayerProfileDto> profile = extractor.extract(html);

        assertThat(profile).isEmpty();
    }

    private static String page(String name) throws IOException {
        return new ClassPathResource("whoscored/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
