package ar.edu.unq.desapp.futbolmarket.service.market;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.HoldingView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.UserNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * El portfolio de una cuenta (research D19). Solo lee: carga las tenencias, las cotizaciones y la
 * ganancia realizada, y el cálculo lo hace {@link Portfolio}. No consulta fuentes externas (US2.10).
 */
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final AppUserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final MarketOrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final QuoteRepository quoteRepository;

    /**
     * El portfolio actual de {@code userId}. El permiso se verifica antes de buscar la cuenta pedida
     * (research D24): un usuario común recibe 403 tanto por una cuenta ajena como por una
     * inexistente, y el administrador recibe 404 por una inexistente.
     *
     * <p>Las cotizaciones de todas las posiciones salen de una sola consulta, sin caché y sin tope
     * de jugadores.</p>
     */
    @Transactional(readOnly = true)
    public Portfolio current(long requesterId, long userId) {
        AppUser requester = userRepository.findById(requesterId).orElseThrow(InvalidCredentialsException::new);
        requester.requireCanAccessAccountOf(userId);
        AppUser account = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (account.getRole() == Role.ADMIN) {
            List<OrderView> initialOrders = orderRepository.findInitialOrdersOf(userId);
            return Portfolio.currentOfSuperuser(account, initialOrders,
                    quotesOf(initialOrders.stream().map(view -> view.player().id()).toList()));
        }
        List<HoldingView> holdings = holdingRepository.findPositionsOf(userId);
        return Portfolio.current(account, holdings,
                quotesOf(holdings.stream().map(view -> view.player().id()).toList()),
                tradeRepository.realizedProfitOf(userId));
    }

    private Map<Long, Quote> quotesOf(List<Long> playerIds) {
        return quoteRepository.findCurrentOf(playerIds).stream()
                .collect(Collectors.toUnmodifiableMap(Quote::playerId, Function.identity()));
    }
}
