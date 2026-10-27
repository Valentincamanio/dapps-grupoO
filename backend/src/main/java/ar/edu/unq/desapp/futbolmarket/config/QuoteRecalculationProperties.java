package ar.edu.unq.desapp.futbolmarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        ScheduleValidation.requireValid(cron, zone, "del recálculo de cotizaciones");
    }
}
