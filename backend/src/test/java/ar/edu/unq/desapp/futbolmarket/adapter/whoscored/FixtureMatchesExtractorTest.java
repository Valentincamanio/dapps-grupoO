package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.FixtureRowDto;
import tools.jackson.databind.json.JsonMapper;

class FixtureMatchesExtractorTest {

    private final FixtureMatchesExtractor extractor = new FixtureMatchesExtractor(JsonMapper.builder().build());

    @Test
    void leeCadaFilaConLasPosicionesDelContrato() throws IOException {
        String html = page("fixtures-everton.html");

        Optional<List<FixtureRowDto>> rows = extractor.extract(html);

        assertThat(rows).hasValueSatisfying(fixtures -> {
            assertThat(fixtures).hasSize(5);
            assertThat(fixtures.get(1)).isEqualTo(new FixtureRowDto(1983549L, "22-08-26", 31L, "Everton", 162L,
                    "Crystal Palace", "FT", 2));
            assertThat(fixtures.get(2).tournamentId()).isEqualTo(29);
        });
    }

    @Test
    void lasPosicionesVaciasDeUnPartidoSinJugarQuedanEnNull() throws IOException {
        String html = page("fixtures-everton.html");

        Optional<List<FixtureRowDto>> rows = extractor.extract(html);

        assertThat(rows).hasValueSatisfying(fixtures -> assertThat(fixtures.getLast())
                .isEqualTo(new FixtureRowDto(1983609L, "17-10-26", 31L, "Everton", 15L, "Chelsea", null, 2)));
    }

    @Test
    void elApostrofoEscapadoQuedaEnElNombre() throws IOException {
        String html = page("fixtures-everton.html");

        Optional<List<FixtureRowDto>> rows = extractor.extract(html);

        assertThat(rows).hasValueSatisfying(fixtures -> assertThat(fixtures.get(3).homeTeamName())
                .isEqualTo("Nott'm Forest"));
    }

    @Test
    void unaComillaDobleDentroDeUnStringConComillasSimplesSeConserva() {
        String html = "fixtureMatches: [[1,1,'01-01-27','15:00',5,'Club \"Los Pumas\"',0,6,'Otro',0,'vs',,0,0,,"
                + "'2026/2027','Premier League','-1',2]]";

        Optional<List<FixtureRowDto>> rows = extractor.extract(html);

        assertThat(rows).hasValueSatisfying(fixtures -> assertThat(fixtures.getFirst().homeTeamName())
                .isEqualTo("Club \"Los Pumas\""));
    }

    @Test
    void sinElMarcadorEsUnRechazo() throws IOException {
        String html = page("rejected-403.html");

        Optional<List<FixtureRowDto>> rows = extractor.extract(html);

        assertThat(rows).isEmpty();
    }

    private static String page(String name) throws IOException {
        return new ClassPathResource("whoscored/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
