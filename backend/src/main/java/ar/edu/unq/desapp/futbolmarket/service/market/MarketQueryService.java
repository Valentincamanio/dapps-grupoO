package ar.edu.unq.desapp.futbolmarket.service.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import lombok.RequiredArgsConstructor;

/**
 * Las consultas del mercado que no cambian nada: el listado público (research D22). No bloquea ni
 * consulta fuentes externas.
 */
@Service
@RequiredArgsConstructor
public class MarketQueryService {
    private final MarketOrderRepository orderRepository;

    /**
     * Una hoja de las órdenes con tokens pendientes que cumplen el filtro, de la más vieja a la más
     * nueva. Es público: no recibe a quien pide, y ninguna orden lleva datos de su dueño más allá de
     * su tipo.
     */
    @Transactional(readOnly = true)
    public QuotePage<OrderView> market(MarketOrderFilter filter, int page, int size) {
        return orderRepository.findMarket(filter, page, size);
    }
}
