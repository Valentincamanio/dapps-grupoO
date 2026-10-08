package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import static ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataClient.COUNTER_RESET_HEADER;
import static ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataClient.REQUESTS_AVAILABLE_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataClientConfig;
import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Prueba el adapter contra un servidor simulado, con las fixtures armadas a partir de las respuestas
 * reales del contrato. Nunca llama a la API real y nunca duerme: las esperas las registra
 * {@link RecordingSleeper} y el reloj está fijo.
 */
class FootballDataAdapterTest {

    private static final String TOKEN = "token-de-prueba-del-adapter-0123456789";
    private static final String BASE_URL = "http://football-data.invalid/v4";
    private static final String PL_TEAMS_URL = BASE_URL + "/competitions/PL/teams";
    private static final String PL_MATCHES_URL = BASE_URL + "/competitions/PL/matches";
    private static final String BL1_TEAMS_URL = BASE_URL + "/competitions/BL1/teams";
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");

    private static final Resource TEAMS_PL = new ClassPathResource("footballdata/teams-pl.json");
    private static final Resource MATCHES_PL = new ClassPathResource("footballdata/matches-pl.json");
    private static final Resource TEAMS_EMPTY = new ClassPathResource("footballdata/teams-empty.json");
    private static final Resource ERROR_403 = new ClassPathResource("footballdata/error-403.json");

    private static final String TOO_MANY_REQUESTS_REASON = "Se excedió el límite de consultas de la fuente (429).";
    private static final String SERVER_ERROR_REASON = "La fuente respondió con un error (5xx).";
    private static final String INVALID_FORMAT_REASON = "La respuesta de la fuente no tiene el formato esperado.";

    private final RestClient.Builder builder = FootballDataClientConfig.applyDefaults(RestClient.builder(), properties());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RecordingSleeper sleeper = new RecordingSleeper();
    private final FootballDataAdapter adapter = new FootballDataAdapter(
            new FootballDataClient(builder.build(), sleeper, Clock.fixed(NOW, ZoneOffset.UTC)),
            new FootballDataMapper());

    @Test
    void traeLaLigaConDosConsultasConLaCredencialEIgnoraLosCamposDesconocidos() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        LeagueSnapshot snapshot = adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(snapshot.season()).isEqualTo(new Season(
                null, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 6));
        assertThat(snapshot.teams())
                .extracting(TeamSnapshot::externalId, TeamSnapshot::name, TeamSnapshot::crest)
                .containsExactly(
                        tuple("64", "Liverpool FC", "https://crests.football-data.org/64.png"),
                        tuple("61", "Chelsea FC", "https://crests.football-data.org/61.png"));
        assertThat(snapshot.teams())
                .flatExtracting(TeamSnapshot::squad)
                .extracting(PlayerSnapshot::name, PlayerSnapshot::position)
                .containsExactly(
                        tuple("Alisson Becker", Position.GOALKEEPER),
                        tuple("Kostas Tsimikas", Position.DEFENDER),
                        tuple("Mediocampista de Liverpool FC", Position.MIDFIELDER),
                        tuple("Federico Chiesa", Position.FORWARD),
                        tuple("Mahdi Nicoll-Jazuli", null));
        assertThat(snapshot.matches())
                .extracting(MatchSnapshot::externalId, MatchSnapshot::status)
                .containsExactly(
                        tuple("560542", MatchStatus.FINISHED),
                        tuple("560593", MatchStatus.TIMED),
                        tuple("560571", MatchStatus.POSTPONED));
        assertThat(snapshot.matches().getFirst()).isEqualTo(new MatchSnapshot("560542", "2502",
                Instant.parse("2026-08-21T19:00:00Z"), 1, MatchStatus.FINISHED, "57", "1076", new Score(3, 0),
                new Score(2, 0), MatchWinner.HOME_TEAM));
        assertThat(sleeper.sleeps()).isEmpty();
    }

    static Stream<Arguments> failedResponses() {
        return Stream.of(
                arguments(named("403 con el cuerpo real de la fuente",
                                withStatus(HttpStatus.FORBIDDEN).body(ERROR_403).contentType(MediaType.APPLICATION_JSON)),
                        "La fuente rechazó la credencial o el recurso no está disponible en el plan contratado (403)."),
                arguments(named("404", withResourceNotFound()), "La fuente no encontró la competición (404)."),
                arguments(named("400", withBadRequest()), "La fuente rechazó la consulta (400)."),
                arguments(named("500", withServerError()), SERVER_ERROR_REASON),
                arguments(named("502 con un cuerpo HTML",
                                withStatus(HttpStatus.BAD_GATEWAY).body("<html>Bad Gateway</html>")
                                        .contentType(MediaType.TEXT_HTML)),
                        SERVER_ERROR_REASON),
                arguments(named("timeout", withException(new HttpTimeoutException("request timed out"))),
                        "La fuente no respondió a tiempo."),
                arguments(named("conexión rechazada", withException(new ConnectException("Connection refused"))),
                        "No se pudo conectar con la fuente."),
                arguments(named("JSON inválido", withSuccess("{ esto no es json", MediaType.APPLICATION_JSON)),
                        INVALID_FORMAT_REASON),
                arguments(named("respuesta sin cuerpo", withSuccess()), INVALID_FORMAT_REASON));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedResponses")
    void unaRespuestaFallidaTerminaEnUnaExternalSourceExceptionConSuMotivoYSinElToken(ResponseCreator response,
                                                                                      String reason) {
        expect(PL_TEAMS_URL).andRespond(response);

        assertThatThrownBy(() -> adapter.fetchLeague(League.PREMIER))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage(reason)
                .message()
                .doesNotContain(TOKEN);
        server.verify();
    }

    @Test
    void unaLigaSinEquiposFallaConElMotivoDeLaFuenteSinEquipos() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_EMPTY));
        expect(PL_MATCHES_URL).andRespond(withSuccess("{\"matches\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetchLeague(League.PREMIER))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("La fuente no informó ningún equipo para la liga.");
        server.verify();
    }

    @Test
    void unosEquiposBienYUnosPartidosConErrorHacenFallarLaLiga() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL));
        expect(PL_MATCHES_URL).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchLeague(League.PREMIER))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage(SERVER_ERROR_REASON);
        server.verify();
    }

    @Test
    void anteUn429EsperaLoQuePideLaFuenteYReintentaUnaVez() {
        expect(PL_TEAMS_URL).andRespond(tooManyRequests("42").header(REQUESTS_AVAILABLE_HEADER, "0"));
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        LeagueSnapshot snapshot = adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(42));
        assertThat(snapshot.teams()).hasSize(2);
    }

    @Test
    void dosRespuestas429SeguidasHacenFallarLaLigaConUnaSolaEspera() {
        expect(PL_TEAMS_URL).andRespond(tooManyRequests("42"));
        expect(PL_TEAMS_URL).andRespond(tooManyRequests("42"));

        assertThatThrownBy(() -> adapter.fetchLeague(League.PREMIER))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage(TOO_MANY_REQUESTS_REASON);
        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(42));
    }

    @Test
    void unaRespuesta429SinElHeaderDeRenovacionEsperaSesentaSegundos() {
        expect(PL_TEAMS_URL).andRespond(withTooManyRequests());
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void unaRespuesta429QuePideMasDeDosMinutosFallaSinEsperar() {
        expect(PL_TEAMS_URL).andRespond(tooManyRequests("121"));

        assertThatThrownBy(() -> adapter.fetchLeague(League.PREMIER))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage(TOO_MANY_REQUESTS_REASON);
        server.verify();
        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void unaRespuesta429QuePideJustoDosMinutosTodaviaEspera() {
        expect(PL_TEAMS_URL).andRespond(tooManyRequests("120"));
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(120));
    }

    @Test
    void sinConsultasDisponiblesLaConsultaSiguienteEsperaLaRenovacion() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL).header(REQUESTS_AVAILABLE_HEADER, "0")
                .header(COUNTER_RESET_HEADER, "30"));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void conConsultasDisponiblesLaConsultaSiguienteNoEspera() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL).header(REQUESTS_AVAILABLE_HEADER, "9")
                .header(COUNTER_RESET_HEADER, "30"));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).isEmpty();
    }

    /**
     * Con el reloj fijo, la renovación nunca "pasa": si el estado no se limpiara después de esperar,
     * cada consulta siguiente volvería a esperar. La consulta de partidos falla sin respuesta, así
     * que ninguna respuesta nueva pisa el estado.
     */
    @Test
    void despuesDeEsperarLaRenovacionLaConsultaSiguienteNoVuelveAEsperar() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL).header(REQUESTS_AVAILABLE_HEADER, "0")
                .header(COUNTER_RESET_HEADER, "30"));
        expect(PL_MATCHES_URL).andRespond(withException(new ConnectException("Connection reset")));
        expect(BL1_TEAMS_URL).andRespond(withServerError());

        fetchIgnoringFailure(League.PREMIER);
        fetchIgnoringFailure(League.BUNDESLIGA);

        server.verify();
        assertThat(sleeper.sleeps()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void unaRespuestaSinElHeaderDeConsultasDisponiblesNoDejaUnaEsperaPendiente() {
        expect(PL_TEAMS_URL).andRespond(json(TEAMS_PL).header(COUNTER_RESET_HEADER, "30"));
        expect(PL_MATCHES_URL).andRespond(json(MATCHES_PL));

        adapter.fetchLeague(League.PREMIER);

        server.verify();
        assertThat(sleeper.sleeps()).isEmpty();
    }

    private ResponseActions expect(String url) {
        return server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(FootballDataClientConfig.AUTH_TOKEN_HEADER, TOKEN));
    }

    /**
     * Las fallas se esperan: lo que se prueba es si hubo esperas antes de cada consulta.
     */
    private void fetchIgnoringFailure(League league) {
        assertThatThrownBy(() -> adapter.fetchLeague(league)).isInstanceOf(ExternalSourceException.class);
    }

    private static DefaultResponseCreator json(Resource body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static DefaultResponseCreator tooManyRequests(String counterResetSeconds) {
        return withTooManyRequests().header(COUNTER_RESET_HEADER, counterResetSeconds);
    }

    private static FootballDataProperties properties() {
        return new FootballDataProperties(TOKEN, URI.create(BASE_URL), Duration.ofSeconds(10), Duration.ofSeconds(30),
                new FootballDataProperties.Sync("-", "America/Argentina/Buenos_Aires", false));
    }
}
