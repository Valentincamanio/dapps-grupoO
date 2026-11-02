package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostedWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.BoostChangeConflictException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.BoostRepository;
import lombok.RequiredArgsConstructor;

/**
 * Los potenciados de cada semana: consultarlos y que el administrador los elija. Las reglas (cuatro
 * jugadores distintos o ninguno, de 200 % a 400 % y solo semanas que no cerraron) son de
 * {@link WeekBoosts}; acá se resuelven los jugadores y se guarda.
 *
 * <p>Cambiar los potenciados no toca ninguna cotización: rigen recién en el cierre de su semana.</p>
 */
@Service
@RequiredArgsConstructor
public class BoostService {

    private final BoostRepository boostRepository;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    /**
     * Los potenciados de la semana en curso, en hora de Argentina; puede no tener ninguno.
     */
    public BoostedWeek current() {
        return show(boostRepository.findWeek(QuoteWeek.containing(clock.instant())));
    }

    /**
     * Los potenciados de la semana que contiene ese día, de cualquier semana.
     */
    public BoostedWeek week(LocalDate day) {
        return show(boostRepository.findWeek(weekOf(day)));
    }

    /**
     * Reemplaza los potenciados de la semana que contiene ese día. Si un jugador no existe, 404 sin
     * guardar nada. Si otro cambio de la misma semana se cruza con este, 409.
     */
    public BoostedWeek replace(LocalDate day, List<PlayerBoost> boosts) {
        WeekBoosts chosen = WeekBoosts.choose(weekOf(day), boosts, clock.instant());
        chosen.playerIds().stream()
                .filter(playerId -> !playerRepository.existsById(playerId))
                .findFirst()
                .ifPresent(playerId -> {
                    throw new PlayerNotFoundException(playerId);
                });
        try {
            return show(boostRepository.replace(chosen));
        } catch (DataIntegrityViolationException e) {
            throw new BoostChangeConflictException();
        }
    }

    private BoostedWeek show(WeekBoosts boosts) {
        return BoostedWeek.of(boosts, playerRepository.findIndexedByIds(boosts.playerIds()), clock.instant());
    }

    private static QuoteWeek weekOf(LocalDate day) {
        return QuoteWeek.containing(day.atStartOfDay(QuoteWeek.ZONE).toInstant());
    }
}
