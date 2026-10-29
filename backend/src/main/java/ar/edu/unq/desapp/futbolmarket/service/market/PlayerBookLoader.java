package ar.edu.unq.desapp.futbolmarket.service.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PlayerBook;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Arma el libro de un jugador para un pedido, bloqueando antes de leer y siempre en el mismo orden
 * (research D13): primero el libro (la fila de la orden inicial), después las cuentas en orden
 * ascendente de id. Corre en la transacción de quien lo llama, que es la que mantiene los bloqueos.
 *
 * <p>Las órdenes abiertas se leen apenas se toma el candado del libro, que es lo que las serializa,
 * porque sus dueños son parte de las cuentas a bloquear. Las cuentas no se leen antes de su
 * bloqueo. Qué cuentas se bloquean lo decide el modelo ({@link PlayerBook#accountsToLock}).</p>
 */
@Component
@RequiredArgsConstructor
public class PlayerBookLoader {
    private final MarketOrderRepository orderRepository;
    private final AppUserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final PlayerRepository playerRepository;
    private final QuoteRepository quoteRepository;

    /**
     * Sin orden inicial el libro queda no operable: el modelo rechaza el pedido.
     */
    public PlayerBook load(long playerId, long requesterId, OrderSide side, Instant at) {
        Optional<MarketOrder> initialOrder = orderRepository.lockBook(playerId);
        List<MarketOrder> openOrders = orderRepository.findOpenOfPlayer(playerId);
        Map<Long, AppUser> accounts = userRepository.lockAllInOrder(
                PlayerBook.accountsToLock(requesterId, side, initialOrder, openOrders));
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
        return new PlayerBook(player, quoteRepository.findInForceAt(playerId, at), initialOrder, openOrders, accounts,
                holdingRepository.findOfPlayer(playerId, accounts.keySet()));
    }

    /**
     * El libro para cancelar una orden (research D16). El jugador se lee sin cargar la orden, para
     * bloquear su libro antes; después se bloquea la cuenta de quien pide y recién entonces se leen
     * la orden, su tenencia y el jugador. Si quien pide no es el dueño, el modelo lo rechaza (403).
     * Una orden inexistente es 404 antes de bloquear nada.
     */
    public Cancellation loadForCancel(long orderId, long requesterId, Instant at) {
        long playerId = orderRepository.findPlayerIdOf(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Optional<MarketOrder> initialOrder = orderRepository.lockBook(playerId);
        Map<Long, AppUser> accounts = userRepository.lockAllInOrder(Set.of(requesterId));
        MarketOrder order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
        PlayerBook book = new PlayerBook(player, quoteRepository.findInForceAt(playerId, at), initialOrder, List.of(),
                accounts, holdingRepository.findOfPlayer(playerId, accounts.keySet()));
        return new Cancellation(book, order);
    }

    /**
     * El libro y la orden que se cancela, leída después de bloquear.
     */
    public record Cancellation(PlayerBook book, MarketOrder order) {
    }
}
