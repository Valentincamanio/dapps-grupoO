package ar.edu.unq.desapp.futbolmarket.service.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.HoldingView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderView;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeReplay;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.HoldingRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.TradeRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * El portfolio de una cuenta (research D19 y D20). Solo lee: carga las tenencias o las operaciones,
 * las cotizaciones y la ganancia realizada, y el cálculo lo hacen {@link Portfolio} y
 * {@link TradeReplay}. No consulta fuentes externas (US2.10).
 */
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final AppUserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final MarketOrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final QuoteRepository quoteRepository;
    private final PlayerRepository playerRepository;
    private final Clock clock;

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
        AppUser account = AccountAccess.accountOf(userRepository, requesterId, userId);
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

    /**
     * El portfolio de {@code userId} al final de ese día en hora de Argentina (research D20). El
     * permiso y la cuenta, como en {@link #current}; después la fecha, en este orden y todas con 400:
     * el formato, que no sea futura y que no sea anterior al registro (FR-041).
     *
     * <p>Reproduce las operaciones anteriores al final del día (para el superusuario, también sus
     * órdenes iniciales creadas antes) y las valúa con la cotización que regía entonces: las
     * cotizaciones y los jugadores salen de una consulta cada uno.</p>
     *
     * @param date el día como {@code AAAA-MM-DD}, tal como llegó
     */
    @Transactional(readOnly = true)
    public Portfolio at(long requesterId, long userId, String date) {
        AppUser account = AccountAccess.accountOf(userRepository, requesterId, userId);
        LocalDate day = QuoteDay.requireNotFuture(QuoteDay.parse(date), QuoteDay.today(clock.instant()));
        account.requireRegisteredBy(day);
        Instant end = QuoteDay.endOf(day);
        List<Trade> trades = tradeRepository.findOfUserBefore(userId, end);
        TradeReplay replay = account.getRole() == Role.ADMIN
                ? TradeReplay.ofSuperuser(orderRepository.findInitialOrdersCreatedBefore(userId, end), trades)
                : TradeReplay.ofUser(userId, trades);
        return Portfolio.at(userId, day, balanceAt(account, end), replay,
                playerRepository.findIndexedByIds(replay.playerIds()),
                quoteRepository.findAtOf(replay.playerIds(), end));
    }

    /**
     * El saldo total al final del día sale de las operaciones (research D20): la primera posterior
     * solo se busca si no hay ninguna anterior.
     */
    private BigDecimal balanceAt(AppUser account, Instant end) {
        long userId = account.getId();
        Trade lastBefore = tradeRepository.findLastOfUserBefore(userId, end).orElse(null);
        Trade firstAtOrAfter = lastBefore != null ? null
                : tradeRepository.findFirstOfUserAtOrAfter(userId, end).orElse(null);
        return TradeReplay.balanceAt(userId, lastBefore, firstAtOrAfter, account.getBalance());
    }

    private Map<Long, Quote> quotesOf(List<Long> playerIds) {
        return quoteRepository.findCurrentOf(playerIds).stream()
                .collect(Collectors.toUnmodifiableMap(Quote::playerId, Function.identity()));
    }
}
