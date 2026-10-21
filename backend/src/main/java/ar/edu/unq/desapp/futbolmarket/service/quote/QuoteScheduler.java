package ar.edu.unq.desapp.futbolmarket.service.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import lombok.RequiredArgsConstructor;

/**
 * El recálculo semanal: los lunes a las 06:00 de Argentina, dos horas después de la cadena de
 * sincronización, según el cron y la zona de la configuración (FR-047 y research D17). Con el cron
 * {@code "-"}, como en el perfil test, la tarea no se programa.
 *
 * <p>Una corrida perdida, por ejemplo con la aplicación apagada, no se recupera acá: la recupera el
 * recálculo al arrancar, si quedaron semanas cerradas sin cotizar.</p>
 */
@Component
@RequiredArgsConstructor
public class QuoteScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuoteScheduler.class);

    private final QuoteRecalculationService recalculationService;

    /**
     * Si hay otro recálculo o una sincronización en curso, este se omite: no se encola ni se
     * reintenta (US6.4).
     */
    @Scheduled(cron = "${futbolmarket.quotes.recalculation.cron}",
            zone = "${futbolmarket.quotes.recalculation.zone}")
    public void runWeeklyRecalculation() {
        try {
            recalculationService.recalculate(RecalculationOrigin.WEEKLY);
        } catch (RecalculationInProgressException | RecalculationBlockedBySyncException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite el recálculo semanal: {}", reason);
        }
    }
}
