package ar.edu.unq.desapp.futbolmarket.service.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.QuoteRecalculationProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import lombok.RequiredArgsConstructor;

/**
 * El recálculo al arrancar (FR-047, US6.2 y US6.3; research D17). Corre cuando la aplicación ya
 * levantó, en un hilo del executor de Boot, así no demora el arranque, y solo si quedaron semanas
 * cerradas sin cotizar.
 *
 * <p>Con {@code quotes.recalculation.on-startup} en {@code false}, como en el perfil test, termina
 * sin hacer nada.</p>
 */
@Component
@RequiredArgsConstructor
public class QuoteStartupRecalculation {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuoteStartupRecalculation.class);

    private final QuoteRecalculationProperties properties;
    private final QuoteRecalculationService recalculationService;

    /**
     * Si hay otro recálculo o una sincronización en curso, por ejemplo la puesta al día de arranque,
     * este se omite y las semanas quedan para el recálculo semanal (US6.4).
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void recalculatePendingWeeks() {
        if (!properties.onStartup() || !recalculationService.hasClosedUnquotedWeeks()) {
            return;
        }
        try {
            recalculationService.recalculate(RecalculationOrigin.STARTUP);
        } catch (RecalculationInProgressException | RecalculationBlockedBySyncException e) {
            String reason = e.getMessage();
            LOGGER.warn("Se omite el recálculo de arranque: {} Las semanas pendientes quedan para el recálculo "
                    + "semanal.", reason);
        }
    }
}
