package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchCentreDto;
import tools.jackson.databind.json.JsonMapper;

class MatchCentreExtractorTest {

    private final MatchCentreExtractor extractor = new MatchCentreExtractor(JsonMapper.builder().build());

    @Test
    void leeElObjetoDeLaPaginaDeUnPartidoAunqueUnNombreTengaUnaLlave() throws IOException {
        String html = page("match-1983600.html");

        Optional<Optional<MatchCentreDto>> extracted = extractor.extract(html);

        assertThat(extracted).hasValueSatisfying(data -> assertThat(data).hasValueSatisfying(matchCentre -> {
            assertThat(matchCentre.statusCode()).isEqualTo(6);
            assertThat(matchCentre.expandedMaxMinute()).isEqualTo(97);
            assertThat(matchCentre.home().teamId()).isEqualTo(19L);
            assertThat(matchCentre.away().teamId()).isEqualTo(23L);
            assertThat(matchCentre.home().players()).hasSize(6);
            assertThat(matchCentre.events()).hasSize(8);
        }));
    }

    @Test
    void conMatchCentreDataNullLaPaginaEsValidaPeroNoTieneDatos() throws IOException {
        String html = page("match-unavailable.html");

        Optional<Optional<MatchCentreDto>> extracted = extractor.extract(html);

        assertThat(extracted).contains(Optional.empty());
    }

    @Test
    void unaPaginaSinElBloqueEsUnRechazo() throws IOException {
        String html = page("rejected-403.html");

        Optional<Optional<MatchCentreDto>> extracted = extractor.extract(html);

        assertThat(extracted).isEmpty();
    }

    @Test
    void unBloqueQueNoEsJsonValidoEsUnRechazo() {
        String html = "<script>require.config.params[\"args\"] = { "
                + "matchCentreData: {\"statusCode\": 6, \"home\": }, };";

        Optional<Optional<MatchCentreDto>> extracted = extractor.extract(html);

        assertThat(extracted).isEmpty();
    }

    private static String page(String name) throws IOException {
        return new ClassPathResource("whoscored/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
