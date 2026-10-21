package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.config.CacheConfig;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.PlayerWithoutQuoteException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteNotFoundAtDateException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * Las consultas públicas de cotizaciones (research D20). Solo lee: no consulta fuentes externas.
 *
 * <p>La cotización actual de un jugador y el ranking se cachean (research D21); el caché lo vacía
 * {@link QuoteCacheInvalidator} después de cada cambio confirmado. Un 404 no se cachea.</p>
 *
 * <p>Busca la cotización primero y al jugador solo si no la encuentra, para distinguir un jugador
 * inexistente de uno sin cotización sin una consulta más en el caso común.</p>
 */
@Service
@RequiredArgsConstructor
public class QuoteQueryService {
    public static final int MAX_BATCH_SIZE = 50;

    private final QuoteRepository quoteRepository;
    private final PlayerRepository playerRepository;
    private final StrategyRepository strategyRepository;
    private final Clock clock;

    @Cacheable(cacheNames = CacheConfig.QUOTES_CURRENT, key = "#playerId")
    public Quote getCurrent(long playerId) {
        return quoteRepository.findCurrent(playerId).orElseThrow(() -> {
            requirePlayer(playerId);
            return new PlayerWithoutQuoteException(playerId);
        });
    }

    /**
     * La cotización que regía al final de ese día, en hora de Argentina (FR-043). La fecha se
     * valida antes de buscar al jugador.
     */
    public Quote getAt(long playerId, String date) {
        LocalDate day = QuoteDay.requireNotFuture(QuoteDay.parse(date), QuoteDay.today(clock.instant()));
        return quoteRepository.findAt(playerId, QuoteDay.endOf(day)).orElseThrow(() -> {
            requirePlayer(playerId);
            return new QuoteNotFoundAtDateException(playerId, day);
        });
    }

    /**
     * La evolución del jugador entre dos días, los dos incluidos. Sin {@code from} arranca desde la
     * primera cotización; sin {@code to} llega hasta hoy. Un jugador sin cotizaciones en el rango
     * devuelve una hoja vacía; uno inexistente, 404.
     */
    public QuotePage<Quote> getHistory(long playerId, String from, String to, int page, int size) {
        LocalDate fromDay = from == null ? null : QuoteDay.parse(from);
        LocalDate toDay = to == null ? null : QuoteDay.parse(to);
        if (fromDay != null && toDay != null) {
            QuoteDay.requireOrdered(fromDay, toDay);
        }
        Instant start = fromDay == null ? Instant.EPOCH : QuoteDay.startOf(fromDay);
        Instant end = QuoteDay.endOf(toDay == null ? QuoteDay.today(clock.instant()) : toDay);
        QuotePage<Quote> history = quoteRepository.findHistory(playerId, start, end, page, size);
        if (history.totalElements() == 0) {
            requirePlayer(playerId);
        }
        return history;
    }

    /**
     * La cotización actual de varios jugadores en una sola consulta, para la pizarra (FR-062). Los
     * que no tienen cotización o no existen no figuran; el resto sale en el orden pedido, sin
     * repetidos. No se cachea.
     */
    public List<Quote> getCurrentOf(List<Long> playerIds) {
        if (playerIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Se pidieron " + playerIds.size() + " jugadores y el máximo es "
                    + MAX_BATCH_SIZE + ".");
        }
        List<Long> distinct = playerIds.stream().distinct().toList();
        return quoteRepository.findCurrentOf(distinct).stream()
                .sorted(Comparator.comparingInt(quote -> distinct.indexOf(quote.playerId())))
                .toList();
    }

    /**
     * Una hoja del ranking con la estrategia activa (FR-045). La clave del caché son el filtro, la
     * hoja y el tamaño.
     */
    @Cacheable(cacheNames = CacheConfig.QUOTES_RANKING)
    public RankingPage getRanking(PlayerFilter filter, int page, int size) {
        return new RankingPage(quoteRepository.findRanking(filter, page, size),
                strategyRepository.findCatalog().activeVersion().ref());
    }

    private void requirePlayer(long playerId) {
        if (playerRepository.findById(playerId).isEmpty()) {
            throw new PlayerNotFoundException(playerId);
        }
    }
}
