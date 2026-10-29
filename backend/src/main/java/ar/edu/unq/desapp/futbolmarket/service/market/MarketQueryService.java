package ar.edu.unq.desapp.futbolmarket.service.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeView;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Las consultas del mercado que no cambian nada: el listado público (research D22), y el historial y
 * las órdenes de una cuenta (US6). No bloquea ni consulta fuentes externas.
 */
@Service
@RequiredArgsConstructor
public class MarketQueryService {
    private final MarketOrderRepository orderRepository;
    private final AppUserRepository userRepository;
    private final TradeRepository tradeRepository;

    /**
     * Una hoja de las órdenes con tokens pendientes que cumplen el filtro, de la más vieja a la más
     * nueva. Es público: no recibe a quien pide, y ninguna orden lleva datos de su dueño más allá de
     * su tipo.
     */
    @Transactional(readOnly = true)
    public QuotePage<OrderView> market(MarketOrderFilter filter, int page, int size) {
        return orderRepository.findMarket(filter, page, size);
    }

    /**
     * Una hoja del historial de {@code userId}, de la operación más nueva a la más vieja (FR-043). El
     * permiso se verifica antes de buscar la cuenta, como en el portfolio.
     */
    @Transactional(readOnly = true)
    public QuotePage<TradeView> transactions(long requesterId, long userId, TradeFilter filter, int page, int size) {
        AccountAccess.accountOf(userRepository, requesterId, userId);
        return tradeRepository.findHistory(userId, filter, page, size);
    }

    /**
     * Una hoja de las órdenes de {@code userId} en cualquier estado, de la más nueva a la más vieja,
     * con el mismo permiso que el historial.
     */
    @Transactional(readOnly = true)
    public QuotePage<OrderView> ordersOf(long requesterId, long userId, int page, int size) {
        AccountAccess.accountOf(userRepository, requesterId, userId);
        return orderRepository.findOfOwner(userId, page, size);
    }
}
