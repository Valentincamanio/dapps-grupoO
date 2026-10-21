package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.BlockedPage;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;

/**
 * Pide las páginas de WhoScored de a una, con el ritmo, el reintento y el corte de research D6.
 *
 * <p>Antes de cada pedido espera lo que falta para cumplir {@code request-interval} desde que
 * terminó la respuesta anterior, medido con el {@link Clock}. Ese momento se conserva entre
 * corridas, así una corrida manual inmediatamente después de otra también respeta la pausa.</p>
 *
 * <p>Una página es válida solo si responde 200 con cuerpo y el extractor encuentra su bloque. Ante
 * el primer rechazo espera {@code retry-wait}, que ya cubre la pausa, y reintenta una vez; si el
 * reintento también falla, la fuente está bloqueando y la corrida se corta con
 * {@link SourceBlockedException}.</p>
 *
 * <p>No es concurrente: hay a lo sumo una sincronización a la vez y cada corrida usa un solo
 * hilo.</p>
 */
@Component
public class WhoScoredClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhoScoredClient.class);
    private static final int MILLIS_DECIMALS = 3;

    private final PageDownloader downloader;
    private final WhoScoredSleeper sleeper;
    private final Clock clock;
    private final WhoScoredProperties properties;
    private Instant lastResponseAt;

    public WhoScoredClient(PageDownloader downloader, WhoScoredSleeper sleeper, Clock clock,
                           WhoScoredProperties properties) {
        this.downloader = downloader;
        this.sleeper = sleeper;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * El bloque de la página, leído por el extractor. Un extractor vacío es un rechazo.
     */
    public <T> T fetch(BlockedPage page, Function<String, Optional<T>> extractor) {
        Attempt<T> attempt = withRetry(page.path(), extractor);
        if (attempt.block().isPresent()) {
            return attempt.block().get();
        }
        LOGGER.warn("WhoScored volvió a rechazar la página {} ({}, {} KB): se corta la corrida.", page.path(),
                attempt.page().outcome(), attempt.page().sizeInKb());
        throw new SourceBlockedException(page);
    }

    /**
     * Como {@link #fetch}, con el mismo ritmo y el mismo reintento, para una página que no corta la
     * corrida, como la de un jugador (FR-019d): si el reintento también falla, deja un WARN y
     * devuelve vacío.
     */
    public <T> Optional<T> fetchIfAvailable(String path, Function<String, Optional<T>> extractor) {
        Attempt<T> attempt = withRetry(path, extractor);
        if (attempt.block().isEmpty()) {
            LOGGER.warn("No se pudo leer la página {} de WhoScored: se sigue sin ella.", path);
        }
        return attempt.block();
    }

    /**
     * El pedido y, si es un rechazo, la espera y el único reintento. Devuelve el último intento.
     */
    private <T> Attempt<T> withRetry(String path, Function<String, Optional<T>> extractor) {
        Attempt<T> first = request(path, extractor, awaitRequestInterval());
        if (first.block().isPresent()) {
            return first;
        }
        LOGGER.warn("WhoScored rechazó la página {} ({}, {} KB): se reintenta en {} s.", path,
                first.page().outcome(), first.page().sizeInKb(), seconds(properties.retryWait()));
        sleeper.sleep(properties.retryWait());
        return request(path, extractor, properties.retryWait());
    }

    /**
     * Espera lo que falta para cumplir la pausa y devuelve lo que esperó. Antes del primer pedido no
     * se espera.
     */
    private Duration awaitRequestInterval() {
        if (lastResponseAt == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastResponseAt, clock.instant());
        Duration pause = properties.requestInterval().minus(elapsed);
        if (!pause.isPositive()) {
            return Duration.ZERO;
        }
        sleeper.sleep(pause);
        return pause;
    }

    private <T> Attempt<T> request(String path, Function<String, Optional<T>> extractor, Duration pause) {
        DownloadedPage downloaded = downloader.download(path);
        lastResponseAt = clock.instant();
        LOGGER.info("Pedido a WhoScored: {} ({}, {} KB; pausa previa de {} s).", path, downloaded.outcome(),
                downloaded.sizeInKb(), seconds(pause));
        T block = downloaded.isOk() ? extractor.apply(downloaded.body()).orElse(null) : null;
        return new Attempt<>(downloaded, block);
    }

    /**
     * Los segundos sin ceros de más: {@code 4} o {@code 1.5}.
     */
    private static String seconds(Duration duration) {
        return BigDecimal.valueOf(duration.toMillis()).movePointLeft(MILLIS_DECIMALS).stripTrailingZeros()
                .toPlainString();
    }

    /**
     * La página bajada y su bloque, o {@code null} si fue un rechazo.
     */
    private record Attempt<T>(DownloadedPage page, T extracted) {
        Optional<T> block() {
            return Optional.ofNullable(extracted);
        }
    }
}
