package ar.edu.unq.desapp.futbolmarket.service.market;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Las órdenes iniciales al arrancar (FR-008 y research D8). Corre cuando la aplicación ya levantó,
 * en un hilo del executor de Boot, así no demora el arranque. Para entonces el alta del
 * administrador ya corrió, así que el superusuario existe si está configurado.
 *
 * <p>Con {@code market.initial-orders.automatic} en {@code false}, como en el perfil test, termina
 * sin hacer nada.</p>
 */
@Component
@RequiredArgsConstructor
public class InitialOrderStartup {
    private final InitialOrderService initialOrderService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void openInitialOrders() {
        initialOrderService.onStartup();
    }
}
