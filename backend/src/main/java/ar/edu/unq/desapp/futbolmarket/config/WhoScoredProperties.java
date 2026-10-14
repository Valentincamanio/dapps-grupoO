package ar.edu.unq.desapp.futbolmarket.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades del scraping de WhoScored (research D25).
 *
 * <p>Los headers son los verificados contra el sitio ({@code contracts/whoscored-pages.md}).
 * WhoScored no usa ninguna credencial.</p>
 *
 * <p>La pausa entre pedidos y la espera antes del reintento tienen un mínimo fijado por el spec
 * (FR-042 y FR-043). Se valida al enlazar las propiedades: con un valor menor, la aplicación no
 * arranca, así que el ritmo no se puede bajar por error desde la configuración.</p>
 */
@ConfigurationProperties("futbolmarket.whoscored")
@Validated
public record WhoScoredProperties(

        @NotNull
        URI baseUrl,

        @NotBlank
        String userAgent,

        @NotBlank
        String accept,

        @NotBlank
        String acceptLanguage,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout,

        @NotNull
        Duration requestInterval,

        @NotNull
        Duration retryWait,

        @NotNull
        @Valid
        Snapshot snapshot) {

    /**
     * Pausa mínima entre la respuesta anterior y el pedido siguiente (FR-042).
     */
    public static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(4);

    /**
     * Espera mínima antes del único reintento de una página rechazada (FR-043).
     */
    public static final Duration MIN_RETRY_WAIT = Duration.ofSeconds(30);

    public WhoScoredProperties {
        requirePositive(connectTimeout, "El tiempo máximo de conexión con WhoScored tiene que ser positivo.");
        requirePositive(readTimeout, "El tiempo máximo de lectura de WhoScored tiene que ser positivo.");
        requireAtLeast(requestInterval, MIN_REQUEST_INTERVAL,
                "La pausa entre pedidos a WhoScored tiene que ser de al menos %d segundos.");
        requireAtLeast(retryWait, MIN_RETRY_WAIT,
                "La espera antes de reintentar un pedido a WhoScored tiene que ser de al menos %d segundos.");
    }

    private static void requirePositive(Duration duration, String message) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAtLeast(Duration duration, Duration minimum, String messageFormat) {
        if (duration != null && duration.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(messageFormat.formatted(minimum.toSeconds()));
        }
    }

    /**
     * Ubicación del snapshot de estadísticas, relativa al directorio de trabajo ({@code backend/}
     * con {@code ./gradlew bootRun}). Si el archivo no existe, la sincronización scrapea todo lo
     * pendiente (FR-027).
     */
    public record Snapshot(

            @NotBlank
            String path) {
    }
}
