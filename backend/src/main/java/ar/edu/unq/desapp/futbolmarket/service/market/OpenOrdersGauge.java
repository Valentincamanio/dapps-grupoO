package ar.edu.unq.desapp.futbolmarket.service.market;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * El gauge de órdenes abiertas del mercado (research D7 de la 012, FR-015). Se evalúa en cada
 * lectura, así refleja la base en ese momento, también después de un reinicio. Vive en el servicio
 * porque solo el servicio puede usar la persistencia.
 */
@Component
@RequiredArgsConstructor
public class OpenOrdersGauge implements MeterBinder {
    public static final String OPEN_ORDERS = "futbolmarket.market.orders.open";

    private static final String DESCRIPTION = "Cantidad actual de órdenes abiertas del mercado (pendientes o "
            + "parcialmente ejecutadas).";

    private final MarketOrderRepository marketOrderRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(OPEN_ORDERS, marketOrderRepository, MarketOrderRepository::countOpen)
                .description(DESCRIPTION)
                .strongReference(true)
                .register(registry);
    }
}
