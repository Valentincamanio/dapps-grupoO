package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentFixturesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentMatchDto;
import tools.jackson.databind.json.JsonMapper;

class TournamentFixturesExtractorTest {

    private final TournamentFixturesExtractor extractor = new TournamentFixturesExtractor(JsonMapper.builder().build());

    @Test
    void leeLosTorneosYLosPartidosDelScriptDeLaPaginaDeLaLiga() throws IOException {
        String html = page("league-premier.html");

        Optional<TournamentFixturesDto> fixtures = extractor.extract(html);

        assertThat(fixtures).hasValueSatisfying(tournamentFixtures -> {
            assertThat(tournamentFixtures.tournaments()).hasSize(2);
            assertThat(tournamentFixtures.tournaments().getFirst().tournamentId()).isEqualTo(2);
            assertThat(tournamentFixtures.tournaments().getFirst().matches()).hasSize(3)
                    .first()
                    .isEqualTo(new TournamentMatchDto(31L, "Everton", 15L, "Chelsea"));
        });
    }

    @Test
    void sinElScriptEsUnRechazo() throws IOException {
        String html = page("rejected-403.html");

        Optional<TournamentFixturesDto> fixtures = extractor.extract(html);

        assertThat(fixtures).isEmpty();
    }

    @Test
    void unScriptConUnJsonQueNoSePuedeLeerEsUnRechazo() {
        String html = "<script type=\"application/json\" data-hypernova-key=\"tournamentfixtures\">"
                + "<!--{\"tournaments\":[--></script>";

        Optional<TournamentFixturesDto> fixtures = extractor.extract(html);

        assertThat(fixtures).isEmpty();
    }

    /**
     * Como en la página real: la misma clave está antes en el {@code <div>} que el servidor ya
     * dibujó, y otro script de hypernova va primero.
     */
    @Test
    void conElContenedorDibujadoAntesDelScriptLeeElJsonDelScript() throws IOException {
        String html = page("league-premier-rendered.html");

        Optional<TournamentFixturesDto> fixtures = extractor.extract(html);

        assertThat(fixtures).hasValueSatisfying(tournamentFixtures -> {
            assertThat(tournamentFixtures.tournaments()).singleElement()
                    .satisfies(tournament -> assertThat(tournament.tournamentId()).isEqualTo(2));
            assertThat(tournamentFixtures.tournaments().getFirst().matches()).containsExactly(
                    new TournamentMatchDto(31L, "Everton", 15L, "Chelsea"),
                    new TournamentMatchDto(189L, "Brentford", 26L, "Liverpool"));
        });
    }

    @Test
    void unaClaveQueSoloEstaEnElContenedorEsUnRechazo() {
        String html = "<div data-hypernova-key=\"tournamentfixtures\"><div>Brentford vs Liverpool</div></div>"
                + "<script type=\"text/javascript\">var otro = 1;</script>";

        Optional<TournamentFixturesDto> fixtures = extractor.extract(html);

        assertThat(fixtures).isEmpty();
    }

    private static String page(String name) throws IOException {
        return new ClassPathResource("whoscored/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
