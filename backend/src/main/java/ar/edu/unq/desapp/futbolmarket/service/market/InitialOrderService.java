package ar.edu.unq.desapp.futbolmarket.service.market;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.config.MarketProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActor;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Crea las órdenes iniciales del superusuario, una por jugador del catálogo (FR-007 a FR-009 y
 * research D8). Es idempotente: solo crea las de los jugadores que todavía no la tienen.
 *
 * <p>Dos generaciones a la vez (la del arranque y la escritura de una liga) no duplican nada: cada
 * una bloquea primero la cuenta del superusuario y recién después busca los jugadores sin orden,
 * así la segunda espera y ya ve las de la primera. El índice único sobre la orden inicial de cada
 * jugador es la segunda barrera.</p>
 *
 * <p>Sin superusuario (FR-012) no crea nada y lo advierte en el registro.</p>
 */
@Service
@RequiredArgsConstructor
public class InitialOrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InitialOrderService.class);
    private static final int NO_TOKENS = 0;

    private final Clock clock;
    private final MarketProperties properties;
    private final AppUserRepository userRepository;
    private final MarketOrderRepository orderRepository;
    private final PlayerRepository playerRepository;
    private final AuditRepository auditRepository;

    /**
     * La orden inicial de cada jugador del catálogo que no la tiene, activo o no.
     *
     * @return cuántas creó
     */
    @Transactional
    public int openMissing() {
        return open(orderRepository::playerIdsWithoutInitialOrder);
    }

    /**
     * Lo mismo, entre esos jugadores.
     *
     * @return cuántas creó
     */
    @Transactional
    public int openFor(Collection<Long> playerIds) {
        return open(() -> orderRepository.playerIdsWithoutInitialOrderIn(playerIds));
    }

    /**
     * La entrada del arranque: solo si la generación automática está prendida.
     */
    @Transactional
    public int onStartup() {
        return automatic() ? openMissing() : 0;
    }

    /**
     * La entrada de la sincronización, con los jugadores que acaba de guardar una liga: solo si la
     * generación automática está prendida. Corre en la transacción de la liga.
     */
    @Transactional
    public int afterSync(Collection<Long> playerIds) {
        return automatic() ? openFor(playerIds) : 0;
    }

    private boolean automatic() {
        return properties.initialOrders().automatic();
    }

    private int open(Supplier<List<Long>> playersWithoutInitialOrder) {
        Optional<Long> superuserId = userRepository.findSuperuserId();
        if (superuserId.isEmpty()) {
            LOGGER.warn("No se crean las órdenes iniciales: no existe la cuenta del administrador, que es el "
                    + "superusuario del mercado. Se crearán en el próximo arranque con la cuenta configurada.");
            return 0;
        }
        AppUser superuser = userRepository.lockById(superuserId.get())
                .orElseThrow(() -> new MarketInvariantException("El superusuario " + superuserId.get() + " no existe."));
        List<Long> playerIds = playersWithoutInitialOrder.get();
        if (playerIds.isEmpty()) {
            return 0;
        }
        Instant at = clock.instant();
        Map<Long, Player> players = playerRepository.findAllByIds(playerIds).stream()
                .collect(Collectors.toMap(Player::id, Function.identity()));
        List<MarketOrder> saved = orderRepository.saveAll(players.keySet().stream()
                .sorted()
                .map(playerId -> MarketOrder.initialFor(superuser.getId(), playerId, at))
                .toList());
        auditRepository.saveAll(saved.stream()
                .map(order -> createdEntry(order, players.get(order.getPlayerId()), superuser, at))
                .toList());
        LOGGER.info("Se crearon {} órdenes iniciales del superusuario.", saved.size());
        return saved.size();
    }

    /**
     * El superusuario pasa de 0 a 100 tokens del jugador: el pendiente de su orden nueva.
     */
    private static AuditEntry createdEntry(MarketOrder order, Player player, AppUser superuser, Instant at) {
        PartyChange change = new PartyChange(PartyState.of(superuser, NO_TOKENS), PartyState.of(superuser, order.pending()));
        return AuditEntry.orderCreated(order, player, AuditActor.superuser(superuser.getId()), change, at);
    }
}
