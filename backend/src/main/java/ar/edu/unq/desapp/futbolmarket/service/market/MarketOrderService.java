package ar.edu.unq.desapp.futbolmarket.service.market;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PlayerBook;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Los pedidos del mercado. Cada uno corre en una única transacción (research D13): bloquea y carga
 * el libro del jugador, le pide al modelo que aplique el pedido y guarda lo que devuelve. Todo o
 * nada (FR-025): si el modelo rechaza, no se guarda nada.
 *
 * <p>No decide nada del negocio: las validaciones y el cruce son de {@link PlayerBook}.</p>
 */
@Service
@RequiredArgsConstructor
public class MarketOrderService {
    private final Clock clock;
    private final PlayerRepository playerRepository;
    private final PlayerBookLoader bookLoader;
    private final MarketOrderRepository orderRepository;
    private final AppUserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final AuditRepository auditRepository;

    /**
     * Una compra (US1). Un jugador inexistente es 404 antes de bloquear nada (FR-018).
     */
    @Transactional
    public BookOutcome buy(long requesterId, long playerId, int quantity) {
        Instant at = clock.instant();
        requirePlayer(playerId);
        PlayerBook book = bookLoader.load(playerId, requesterId, OrderSide.BUY, at);
        return save(book.placeBuy(requesterId, quantity, at));
    }

    /**
     * Una venta (US4), por el mismo camino que la compra. El cargador bloquea también a los dueños de
     * todas las compras abiertas del jugador, porque cuáles se usan depende del cruce (research D13).
     */
    @Transactional
    public BookOutcome sell(long requesterId, long playerId, int quantity) {
        Instant at = clock.instant();
        requirePlayer(playerId);
        PlayerBook book = bookLoader.load(playerId, requesterId, OrderSide.SELL, at);
        return save(book.placeSell(requesterId, quantity, at));
    }

    private void requirePlayer(long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new PlayerNotFoundException(playerId);
        }
    }

    /**
     * Guarda en el orden en que cada paso necesita los ids del anterior: órdenes, cuentas y
     * tenencias, operaciones (que apuntan a las órdenes) y auditoría (que apunta a las dos).
     */
    private BookOutcome save(BookOutcome outcome) {
        BookOutcome withOrders = outcome.withSavedOrders(orderRepository.saveAll(outcome.orders()));
        withOrders.accounts().forEach(userRepository::save);
        holdingRepository.saveAll(withOrders.holdings());
        BookOutcome withTrades = withOrders.withSavedTrades(tradeRepository.saveAll(withOrders.trades()));
        auditRepository.saveAll(withTrades.auditEntries());
        return withTrades;
    }
}
