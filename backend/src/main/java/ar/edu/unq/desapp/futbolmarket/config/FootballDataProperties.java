package ar.edu.unq.desapp.futbolmarket.config;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades de la integración con Football-Data.org (research D14).
 *
 * <p>El token no lleva {@code @NotBlank} a propósito: sin él, la aplicación arranca igual y la
 * sincronización queda deshabilitada (FR-041). Solo llega por la variable de entorno
 * {@code FOOTBALL_DATA_TOKEN} y {@code toString} lo enmascara, para que no aparezca en el
 * registro (FR-042).</p>
 *
 * <p>Las duraciones, el cron y la zona se validan al enlazar las propiedades: si alguno es
 * inválido, la aplicación no arranca.</p>
 */
@ConfigurationProperties("futbolmarket.football-data")
@Validated
public record FootballDataProperties(

        String token,

        @NotNull
        URI baseUrl,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout,

        @NotNull
        @Valid
        Sync sync) {

    private static final String MASKED_VALUE = "****";

    public FootballDataProperties {
        requirePositive(connectTimeout, "El tiempo máximo de conexión con Football-Data.org tiene que ser positivo.");
        requirePositive(readTimeout, "El tiempo máximo de lectura de Football-Data.org tiene que ser positivo.");
    }

    /**
     * Indica si hay token. Sin token, la sincronización queda deshabilitada.
     */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "FootballDataProperties[token=%s, baseUrl=%s, connectTimeout=%s, readTimeout=%s, sync=%s]"
                .formatted(MASKED_VALUE, baseUrl, connectTimeout, readTimeout, sync);
    }

    private static void requirePositive(Duration duration, String message) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Corrida semanal y sincronización al arrancar. El cron se interpreta en {@code zone}, y
     * {@code "-"} deshabilita la corrida semanal.
     */
    public record Sync(

            @NotNull
            String cron,

            @NotNull
            String zone,

            boolean onStartup) {

        public Sync {
            if (cron != null && !isValidCron(cron)) {
                throw new IllegalArgumentException(
                        "La expresión cron de la sincronización semanal no es válida: '%s'.".formatted(cron));
            }
            if (zone != null) {
                requireValidZone(zone);
            }
        }

        private static boolean isValidCron(String cron) {
            return Scheduled.CRON_DISABLED.equals(cron) || CronExpression.isValidExpression(cron);
        }

        private static void requireValidZone(String zone) {
            try {
                ZoneId.of(zone);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException(
                        "La zona horaria de la sincronización semanal no es válida: '%s'.".formatted(zone), e);
            }
        }
    }
}
