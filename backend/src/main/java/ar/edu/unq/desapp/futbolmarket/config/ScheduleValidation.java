package ar.edu.unq.desapp.futbolmarket.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/**
 * Validación del cron y la zona de una corrida programada, compartida por la sincronización
 * semanal ({@link FootballDataProperties.Sync}) y el recálculo de cotizaciones
 * ({@link QuoteRecalculationProperties}). {@code "-"} es un cron válido: deshabilita la corrida.
 *
 * <p>Los nulos pasan: de esos se ocupa {@code @NotNull} al enlazar las propiedades.</p>
 */
final class ScheduleValidation {

    private ScheduleValidation() {
        // Solo tiene la validación estática: no se instancia.
    }

    /**
     * {@code run} nombra la corrida en los mensajes, por ejemplo {@code "del recálculo de cotizaciones"}.
     */
    static void requireValid(String cron, String zone, String run) {
        if (cron != null && !isValidCron(cron)) {
            throw new IllegalArgumentException("La expresión cron %s no es válida: '%s'.".formatted(run, cron));
        }
        if (zone != null) {
            requireValidZone(zone, run);
        }
    }

    private static boolean isValidCron(String cron) {
        return Scheduled.CRON_DISABLED.equals(cron) || CronExpression.isValidExpression(cron);
    }

    private static void requireValidZone(String zone, String run) {
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("La zona horaria %s no es válida: '%s'.".formatted(run, zone), e);
        }
    }
}
