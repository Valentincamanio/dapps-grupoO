package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredClientConfig;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties.Snapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedFixture;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedTeam;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prueba el adapter contra un servidor simulado, con las fixtures de las páginas. Nunca consulta
 * WhoScored y nunca duerme: las esperas las registra {@link RecordingWhoScoredSleeper} y el reloj
 * avanza solo cuando el test lo pide.
 */
@ExtendWith(OutputCaptureExtension.class)
class WhoScoredAdapterTest {

    private static final String BASE_URL = "https://www.whoscored.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(4);
    private static final Duration RETRY_WAIT = Duration.ofSeconds(30);
    private static final WhoScoredProperties PROPERTIES = new WhoScoredProperties(URI.create(BASE_URL), USER_AGENT,
            ACCEPT, ACCEPT_LANGUAGE, Duration.ofSeconds(10), Duration.ofSeconds(30), REQUEST_INTERVAL, RETRY_WAIT,
            new Snapshot("snapshot/whoscored-stats.jsonl"));
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final int BYTES_PER_KB = 1024;

    private static final String MATCH_ID = "1983600";
    private static final String MATCH_PATH = "/matches/1983600/live";
    private static final String OTHER_MATCH_PATH = "/matches/1984103/live";
    private static final String LEAGUE_PATH = "/regions/252/tournaments/2/england-premier-league";
    private static final String FIXTURES_PATH = "/teams/31/fixtures";

    private static final Resource MATCH_1983600 = page("match-1983600.html");
    private static final Resource MATCH_SECOND_YELLOW = page("match-second-yellow.html");
    private static final Resource MATCH_UNAVAILABLE = page("match-unavailable.html");
    private static final Resource FIXTURES_EVERTON = page("fixtures-everton.html");
    private static final Resource LEAGUE_PREMIER = page("league-premier.html");
    private static final Resource REJECTED = page("rejected-403.html");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RestClient.Builder builder = WhoScoredClientConfig.applyDefaults(RestClient.builder(), PROPERTIES);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RecordingWhoScoredSleeper sleeper = new RecordingWhoScoredSleeper();
    private final MutableClock clock = new MutableClock(NOW);
    private final WhoScoredAdapter adapter = new WhoScoredAdapter(
            new WhoScoredClient(new HttpPageDownloader(builder.build()), sleeper, clock, PROPERTIES),
            new TournamentFixturesExtractor(jsonMapper), new FixtureMatchesExtractor(jsonMapper),
            new MatchCentreExtractor(jsonMapper), new WhoScoredMapper());

    @Test
    void pideLaPaginaDelPartidoConLosTresHeadersYSinAcceptEncoding() {
        expect(MATCH_PATH)
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(header(HttpHeaders.ACCEPT, ACCEPT))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE))
                .andExpect(headerDoesNotExist(HttpHeaders.ACCEPT_ENCODING))
                .andRespond(html(MATCH_1983600));

        Optional<ReportedMatchStats> stats = adapter.fetchMatchStats(MATCH_ID);

        server.verify();
        assertThat(stats).hasValueSatisfying(reported -> {
            assertThat(reported.whoscoredId()).isEqualTo(MATCH_ID);
            assertThat(reported.finished()).isTrue();
            assertThat(reported.appearances()).hasSize(9);
        });
    }

    @Test
    void cadaPedidoDejaUnInfoConLaRutaElStatusElTamanioYLaPausa(CapturedOutput output) throws IOException {
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));

        adapter.fetchMatchStats(MATCH_ID);

        assertThat(output).contains("Pedido a WhoScored: %s (200, %d KB; pausa previa de 0 s)."
                .formatted(MATCH_PATH, kilobytes(MATCH_1983600)));
    }

    @Test
    void noEsperaAntesDelPrimerPedidoYEsperaCuatroSegundosAntesDelSiguiente() {
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));
        expect(OTHER_MATCH_PATH).andRespond(html(MATCH_SECOND_YELLOW));

        adapter.fetchMatchStats(MATCH_ID);
        adapter.fetchMatchStats("1984103");

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(REQUEST_INTERVAL);
    }

    @Test
    void elTiempoQuePasoDesdeLaRespuestaAnteriorSeDescuentaDeLaPausa(CapturedOutput output) {
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));
        expect(OTHER_MATCH_PATH).andRespond(html(MATCH_SECOND_YELLOW));

        adapter.fetchMatchStats(MATCH_ID);
        clock.advance(Duration.ofSeconds(3));
        adapter.fetchMatchStats("1984103");

        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(1));
        assertThat(output).contains("Pedido a WhoScored: " + OTHER_MATCH_PATH).contains("pausa previa de 1 s).");
    }

    @Test
    void anteUn403EsperaTreintaSegundosSinSumarLaPausaYReintentaUnaVez(CapturedOutput output) {
        expect(MATCH_PATH).andRespond(withStatus(HttpStatus.FORBIDDEN).body(REJECTED).contentType(MediaType.TEXT_HTML));
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));

        Optional<ReportedMatchStats> stats = adapter.fetchMatchStats(MATCH_ID);

        server.verify();
        assertThat(stats).isPresent();
        assertThat(sleeper.sleeps()).containsExactly(RETRY_WAIT);
        assertThat(output)
                .contains("WhoScored rechazó la página " + MATCH_PATH + " (403, 0 KB): se reintenta en 30 s.");
    }

    @Test
    void unaPaginaSinSuBloqueEsUnRechazoYElReintentoLaLee() {
        expect(MATCH_PATH).andRespond(html(REJECTED));
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));

        Optional<ReportedMatchStats> stats = adapter.fetchMatchStats(MATCH_ID);

        server.verify();
        assertThat(stats).isPresent();
        assertThat(sleeper.sleeps()).containsExactly(RETRY_WAIT);
    }

    @Test
    void dosRechazosSeguidosDeLaMismaPaginaCortanLaCorridaSinMasPedidos(CapturedOutput output) {
        expect(MATCH_PATH).andRespond(html(REJECTED));
        expect(MATCH_PATH).andRespond(html(REJECTED));

        assertThatThrownBy(() -> adapter.fetchMatchStats(MATCH_ID))
                .isInstanceOfSatisfying(SourceBlockedException.class, blocked -> assertThat(blocked.page())
                        .isEqualTo(WhoScoredPages.match(MATCH_ID)))
                .hasMessage("WhoScored rechazó dos veces seguidas la página " + MATCH_PATH + ".");
        server.verify();
        assertThat(output).contains("WhoScored volvió a rechazar la página " + MATCH_PATH);
    }

    @Test
    void dosTimeoutsSeguidosCortanSinQueSalgaUnaExcepcionDelClienteHttp(CapturedOutput output) {
        expect(MATCH_PATH).andRespond(withException(new SocketTimeoutException("Read timed out")));
        expect(MATCH_PATH).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> adapter.fetchMatchStats(MATCH_ID)).isInstanceOf(SourceBlockedException.class);
        server.verify();
        assertThat(output).contains("Pedido a WhoScored: " + MATCH_PATH + " (timeout, 0 KB; pausa previa de 0 s).");
    }

    @Test
    void unTimeoutSeReintentaYElReintentoLeeLaPagina() {
        expect(MATCH_PATH).andRespond(withException(new SocketTimeoutException("Read timed out")));
        expect(MATCH_PATH).andRespond(html(MATCH_1983600));

        Optional<ReportedMatchStats> stats = adapter.fetchMatchStats(MATCH_ID);

        server.verify();
        assertThat(stats).isPresent();
        assertThat(sleeper.sleeps()).containsExactly(RETRY_WAIT);
    }

    @Test
    void unPartidoSinDatosDevuelveVacioSinReintentar() {
        expect("/matches/1983609/live").andRespond(html(MATCH_UNAVAILABLE));

        Optional<ReportedMatchStats> stats = adapter.fetchMatchStats("1983609");

        server.verify();
        assertThat(stats).isEmpty();
        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void traeLosEquiposDeLaLigaYLasFilasDelTorneoDeLaPaginaDeFixtures() {
        expect(LEAGUE_PATH).andRespond(html(LEAGUE_PREMIER));
        expect(FIXTURES_PATH).andRespond(html(FIXTURES_EVERTON));

        List<ReportedTeam> teams = adapter.fetchLeagueTeams(League.PREMIER);
        List<ReportedFixture> fixtures = adapter.fetchTeamFixtures(League.PREMIER, "31");

        server.verify();
        assertThat(teams).extracting(ReportedTeam::whoscoredId).containsExactly("31", "15", "19", "23", "32");
        assertThat(fixtures).extracting(ReportedFixture::whoscoredId)
                .containsExactly("1954001", "1983549", "1983560", "1983609");
        assertThat(sleeper.sleeps()).containsExactly(REQUEST_INTERVAL);
    }

    private ResponseActions expect(String path) {
        return server.expect(requestTo(BASE_URL + path)).andExpect(method(HttpMethod.GET));
    }

    private static ResponseCreator html(Resource page) {
        return withSuccess(page, MediaType.TEXT_HTML);
    }

    private static long kilobytes(Resource page) throws IOException {
        return page.getContentAsByteArray().length / BYTES_PER_KB;
    }

    private static Resource page(String name) {
        return new ClassPathResource("whoscored/" + name);
    }

    /**
     * Un reloj que solo avanza cuando el test lo pide, para medir la pausa descontada.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
