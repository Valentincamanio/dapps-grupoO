package ar.edu.unq.desapp.futbolmarket.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Corrida semanal y recálculo al arrancar de las cotizaciones (research D17, FR-047), con el mismo
 * estilo de {@link FootballDataProperties.Sync}. El cron se interpreta en {@code zone}, y
 * {@code "-"} deshabilita la corrida semanal.
 *
 * <p>El cron y la zona se validan al enlazar las propiedades: si alguno es inválido, la aplicación
 * no arranca.</p>
 */
@ConfigurationProperties("futbolmarket.quotes.recalculation")
@Validated
public record QuoteRecalculationProperties(

        @NotNull
        String cron,

        @NotNull
        String zone,

        boolean onStartup) {

    public QuoteRecalculationProperties {
        if (cron != null && !isValidCron(cron)) {
            throw new IllegalArgumentException(
                    "La expresión cron del recálculo de cotizaciones no es válida: '%s'.".formatted(cron));
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
                    "La zona horaria del recálculo de cotizaciones no es válida: '%s'.".formatted(zone), e);
        }
    }
}
