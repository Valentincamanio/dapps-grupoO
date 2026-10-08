package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionMatchesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.CompetitionTeamsDto;
import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto.ErrorDto;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;

/**
 * Las dos consultas de Football-Data.org, con el límite de consultas y la traducción de errores.
 *
 * <p>Ninguna excepción del cliente HTTP sale de acá: todas pasan a {@link ExternalSourceException}
 * con un motivo constante, y la original queda solo como causa (research D5). Nunca se registran
 * los headers, así que tampoco el token ni {@code X-Authenticated-Client}.</p>
 *
 * <p>Límite de consultas (research D6): después de cada respuesta, también las de error, se anota
 * hasta cuándo esperar si ya no quedan consultas en el minuto, y la consulta siguiente espera hasta
 * ese momento. Ante un 429 se espera lo que pide la fuente y se reintenta una sola vez (FR-038).
 * Ese estado sobrevive entre sincronizaciones a propósito: si una anterior consumió el minuto, la
 * siguiente espera en lugar de recibir un 429. No lo comparten dos hilos a la vez, porque hay a lo
 * sumo una sincronización en curso.</p>
 */
@Component
public class FootballDataClient {
    static final String REQUESTS_AVAILABLE_HEADER = "X-Requests-Available-Minute";
    static final String COUNTER_RESET_HEADER = "X-RequestCounter-Reset";
    static final Duration DEFAULT_RESET_WAIT = Duration.ofSeconds(60);
    static final Duration MAX_RESET_WAIT = Duration.ofSeconds(120);

    private static final Logger log = LoggerFactory.getLogger(FootballDataClient.class);

    private static final String TEAMS_PATH = "/competitions/{code}/teams";
    private static final String MATCHES_PATH = "/competitions/{code}/matches";

    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";
    private static final String NETWORK_REASON = "No se pudo conectar con la fuente.";
    private static final String BAD_REQUEST_REASON = "La fuente rechazó la consulta (400).";
    private static final String FORBIDDEN_REASON =
            "La fuente rechazó la credencial o el recurso no está disponible en el plan contratado (403).";
    private static final String NOT_FOUND_REASON = "La fuente no encontró la competición (404).";
    private static final String TOO_MANY_REQUESTS_REASON = "Se excedió el límite de consultas de la fuente (429).";
    private static final String SERVER_ERROR_REASON = "La fuente respondió con un error (5xx).";
    private static final String UNEXPECTED_STATUS_REASON = "La fuente respondió con un estado inesperado.";
    private static final String NO_ERROR_MESSAGE = "sin mensaje";

    private final RestClient restClient;
    private final Sleeper sleeper;
    private final Clock clock;
    private Instant waitUntil;

    public FootballDataClient(@Qualifier("footballDataRestClient") RestClient restClient, Sleeper sleeper,
                              Clock clock) {
        this.restClient = restClient;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public CompetitionTeamsDto fetchTeams(String code) {
        return fetch(TEAMS_PATH, code, CompetitionTeamsDto.class);
    }

    public CompetitionMatchesDto fetchMatches(String code) {
        return fetch(MATCHES_PATH, code, CompetitionMatchesDto.class);
    }

    private <T> T fetch(String path, String code, Class<T> type) {
        Reply<T> reply = send(path, code, type);
        if (reply.status().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            sleeper.sleep(retryWait(reply.counterReset()));
            // La espera ya cubrió la renovación del contador: el reintento no vuelve a esperar.
            waitUntil = null;
            reply = send(path, code, type);
        }
        if (!reply.status().is2xxSuccessful()) {
            throw new ExternalSourceException(reasonFor(reply.status()));
        }
        return reply.body();
    }

    private <T> Reply<T> send(String path, String code, Class<T> type) {
        awaitCounterReset();
        try {
            return restClient.get()
                    .uri(path, code)
                    .exchange((request, response) -> read(response, code, type));
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    private <T> Reply<T> read(ConvertibleClientHttpResponse response, String code, Class<T> type)
            throws IOException {
        HttpStatusCode status = response.getStatusCode();
        recordCounter(response.getHeaders());
        if (status.is2xxSuccessful()) {
            return new Reply<>(status, response.bodyTo(type), null);
        }
        log.warn("Football-Data.org respondió {} para la competición {}: {}", status.value(), code,
                errorMessage(response));
        return new Reply<>(status, null, response.getHeaders().getFirst(COUNTER_RESET_HEADER));
    }

    /**
     * Si la respuesta dice que no quedan consultas en el minuto, la siguiente espera la renovación.
     * Cualquier otra respuesta, también una sin el header, deja salir a la siguiente sin esperar.
     */
    private void recordCounter(HttpHeaders headers) {
        OptionalLong available = parseNonNegative(headers.getFirst(REQUESTS_AVAILABLE_HEADER));
        OptionalLong resetSeconds = parseNonNegative(headers.getFirst(COUNTER_RESET_HEADER));
        boolean exhausted = available.isPresent() && available.getAsLong() == 0 && resetSeconds.isPresent();
        waitUntil = exhausted ? clock.instant().plusSeconds(resetSeconds.getAsLong()) : null;
    }

    /**
     * Después de esperar, o si la renovación ya pasó, el estado se limpia: así un valor viejo nunca
     * provoca una segunda espera.
     */
    private void awaitCounterReset() {
        if (waitUntil == null) {
            return;
        }
        Duration wait = Duration.between(clock.instant(), waitUntil);
        waitUntil = null;
        if (wait.isPositive()) {
            sleeper.sleep(wait);
        }
    }

    /**
     * La espera ante un 429 la dicta la fuente. Si no la informa, se esperan 60 s; si pide más de 2
     * minutos, no se espera y la liga queda fallida (FR-038).
     */
    private static Duration retryWait(String counterReset) {
        OptionalLong seconds = parseNonNegative(counterReset);
        Duration wait = seconds.isPresent() ? Duration.ofSeconds(seconds.getAsLong()) : DEFAULT_RESET_WAIT;
        if (wait.compareTo(MAX_RESET_WAIT) > 0) {
            throw new ExternalSourceException(TOO_MANY_REQUESTS_REASON);
        }
        return wait;
    }

    private static OptionalLong parseNonNegative(String value) {
        if (value == null) {
            return OptionalLong.empty();
        }
        try {
            long number = Long.parseLong(value.trim());
            return number < 0 ? OptionalLong.empty() : OptionalLong.of(number);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static String reasonFor(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return SERVER_ERROR_REASON;
        }
        return switch (HttpStatus.resolve(status.value())) {
            case BAD_REQUEST -> BAD_REQUEST_REASON;
            case FORBIDDEN -> FORBIDDEN_REASON;
            case NOT_FOUND -> NOT_FOUND_REASON;
            case TOO_MANY_REQUESTS -> TOO_MANY_REQUESTS_REASON;
            case null, default -> UNEXPECTED_STATUS_REASON;
        };
    }

    /**
     * El timeout se busca en toda la cadena de causas: puede llegar al abrir la conexión o al leer
     * el cuerpo, y en ese caso viene envuelto en el error de lectura.
     */
    private static ExternalSourceException translate(RestClientException e) {
        if (isTimeout(e)) {
            return new ExternalSourceException(TIMEOUT_REASON, e);
        }
        if (e instanceof ResourceAccessException) {
            return new ExternalSourceException(NETWORK_REASON, e);
        }
        return new ExternalSourceException(ExternalSourceException.INVALID_FORMAT, e);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * El cuerpo de error puede no ser JSON, como el HTML de un 502: en ese caso se registra sin
     * mensaje, para no tapar el status con un error de formato.
     */
    private static String errorMessage(ConvertibleClientHttpResponse response) {
        try {
            ErrorDto error = response.bodyTo(ErrorDto.class);
            return error == null || error.message() == null ? NO_ERROR_MESSAGE : error.message();
        } catch (RestClientException e) {
            return NO_ERROR_MESSAGE;
        }
    }

    private record Reply<T>(HttpStatusCode status, T body, String counterReset) {
    }
}
