package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Propiedades comunes a las sincronizaciones de las dos fuentes (research D25).
 *
 * <p>{@code catchUpAfter} es la antigüedad a partir de la cual la última sincronización completa
 * exitosa de una fuente está vencida y dispara la puesta al día al arrancar (FR-034 y FR-035).
 * Con exactamente esa antigüedad, todavía está al día.</p>
 */
@ConfigurationProperties("futbolmarket.sync")
@Validated
public record SyncProperties(

        @NotNull
        Duration catchUpAfter) {

    public SyncProperties {
        if (catchUpAfter != null && (catchUpAfter.isZero() || catchUpAfter.isNegative())) {
            throw new IllegalArgumentException(
                    "El plazo de vencimiento de la última sincronización completa tiene que ser positivo.");
        }
    }
}
