package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.ClosedBoostWeekException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidBoostException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Los potenciados de una semana: exactamente cuatro jugadores distintos, o ninguno. Rigen en el
 * cierre de esa semana, cuando se calcula su cotización (y si se recotiza o se simula).
 *
 * <p>Se eligen mientras la semana no cerró: también la semana en curso, porque su cotización
 * todavía no se calculó y cambiarlos no recotiza nada.</p>
 */
public record WeekBoosts(QuoteWeek week, List<PlayerBoost> boosts) {
    public static final int PLAYERS_PER_WEEK = 4;

    public WeekBoosts {
        if (week == null) {
            throw new QuoteInvariantException("La semana de los potenciados es obligatoria.");
        }
        boosts = List.copyOf(boosts == null ? List.of() : boosts);
        if (!boosts.isEmpty() && boosts.size() != PLAYERS_PER_WEEK) {
            throw new InvalidBoostException("Una semana lleva exactamente " + PLAYERS_PER_WEEK
                    + " potenciados, o ninguno, y se eligieron " + boosts.size() + ".");
        }
        var players = new HashSet<Long>();
        for (PlayerBoost boost : boosts) {
            if (!players.add(boost.playerId())) {
                throw new InvalidBoostException("El jugador " + boost.playerId() + " está repetido entre los potenciados.");
            }
        }
    }

    public static WeekBoosts none(QuoteWeek week) {
        return new WeekBoosts(week, List.of());
    }

    /**
     * Los potenciados que el administrador elige para la semana. Una semana cerrada ya se cotizó
     * (o está por cotizarse) con los que tenía, así que no se cambia.
     */
    public static WeekBoosts choose(QuoteWeek week, List<PlayerBoost> boosts, Instant now) {
        if (week.isClosedAt(now)) {
            throw new ClosedBoostWeekException(week);
        }
        return new WeekBoosts(week, boosts);
    }

    /**
     * Por cuánto se multiplica el factor de escala del jugador en el cierre: 1 si no está potenciado.
     */
    public BigDecimal multiplierFor(long playerId) {
        return find(playerId).map(PlayerBoost::multiplier).orElse(BigDecimal.ONE);
    }

    public Optional<PlayerBoost> find(long playerId) {
        return boosts.stream().filter(boost -> boost.playerId() == playerId).findFirst();
    }

    public BoostWeekStatus statusAt(Instant now) {
        if (week.isClosedAt(now)) {
            return BoostWeekStatus.CLOSED;
        }
        return now.isBefore(week.start()) ? BoostWeekStatus.UPCOMING : BoostWeekStatus.CURRENT;
    }

    public boolean isEmpty() {
        return boosts.isEmpty();
    }

    public List<Long> playerIds() {
        return boosts.stream().map(PlayerBoost::playerId).toList();
    }
}
