package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.QuoteMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.QuoteSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guarda y lee cotizaciones. A propósito no tiene ningún método de actualización ni de borrado:
 * una cotización guardada no cambia ni se pierde (FR-005).
 */
@Repository
public class QuoteRepository {
    private final QuoteSQLDAO quoteDAO;
    private final PlayerSQLDAO playerDAO;
    private final StrategySQLDAO strategyDAO;
    private final QuoteMapper quoteMapper;
    private final PlayerMapper playerMapper;

    public QuoteRepository(QuoteSQLDAO quoteDAO, PlayerSQLDAO playerDAO, StrategySQLDAO strategyDAO,
                           QuoteMapper quoteMapper, PlayerMapper playerMapper) {
        this.quoteDAO = quoteDAO;
        this.playerDAO = playerDAO;
        this.strategyDAO = strategyDAO;
        this.quoteMapper = quoteMapper;
        this.playerMapper = playerMapper;
    }

    /**
     * Inserta las cotizaciones. El jugador y la versión se referencian sin cargarlos.
     */
    @Transactional
    public List<Quote> saveAll(List<Quote> quotes) {
        List<QuoteSQL> rows = quotes.stream()
                .map(quote -> quoteMapper.toSQL(quote,
                        playerDAO.getReferenceById(quote.playerId()),
                        strategyDAO.getReferenceById(quote.strategy().versionId())))
                .toList();
        return toQuotes(quoteDAO.saveAll(rows));
    }

    /**
     * La base de cada jugador para cotizar la semana (research D9): por id de jugador, su última
     * cotización de las semanas anteriores, incluidas las recotizadas. Quien no figura no tiene
     * ninguna.
     */
    @Transactional(readOnly = true)
    public Map<Long, Quote> findLastBeforeWeek(QuoteWeek week) {
        return toQuotes(quoteDAO.findLastOfEachPlayerBefore(week.monday())).stream()
                .collect(Collectors.toUnmodifiableMap(Quote::playerId, Function.identity()));
    }

    /**
     * Lo que se recotiza de una semana (research D12): por id de jugador, su cotización de mayor
     * vigencia en esa semana. Quien no figura no tiene ninguna en esa semana.
     */
    @Transactional(readOnly = true)
    public Map<Long, Quote> findLatestOfWeek(QuoteWeek week) {
        return toQuotes(quoteDAO.findLatestOfEachPlayerIn(week.monday())).stream()
                .collect(Collectors.toUnmodifiableMap(Quote::playerId, Function.identity()));
    }

    /**
     * La última semana cotizada: la mayor con una cotización {@code CALCULATED}.
     */
    @Transactional(readOnly = true)
    public Optional<QuoteWeek> findLastCalculatedWeek() {
        return Optional.ofNullable(quoteDAO.findLastWeekStartOfType(QuoteType.CALCULATED)).map(QuoteWeek::new);
    }

    /**
     * La cotización actual del jugador: la de mayor vigencia. Ninguna cotización rige desde el
     * futuro (las calculadas son de semanas cerradas y las recotizadas rigen desde que se hacen),
     * así que es también la de mayor vigencia hasta ahora.
     */
    @Transactional(readOnly = true)
    public Optional<Quote> findCurrent(long playerId) {
        return quoteDAO.findCurrent(playerId).map(quoteMapper::toDomain);
    }

    /**
     * La cotización actual de cada uno de esos jugadores, en una sola consulta. Los que no tienen
     * ninguna, o no existen, no figuran. Sin ids no se consulta la base.
     */
    @Transactional(readOnly = true)
    public List<Quote> findCurrentOf(Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        return toQuotes(quoteDAO.findCurrentOf(playerIds));
    }

    /**
     * La cotización actual de cada jugador, para compararla con la simulada (research D19): por id
     * de jugador, la de mayor vigencia, también la de un jugador inactivo. Quien no figura no tiene
     * ninguna.
     */
    @Transactional(readOnly = true)
    public Map<Long, Quote> findLatestByPlayer() {
        return toQuotes(quoteDAO.findLatestOfEachPlayer()).stream()
                .collect(Collectors.toUnmodifiableMap(Quote::playerId, Function.identity()));
    }

    /**
     * La cotización que regía justo antes de ese instante: la última con vigencia anterior a él.
     * Con el comienzo del día siguiente es "la que regía al final del día" (FR-043).
     */
    @Transactional(readOnly = true)
    public Optional<Quote> findAt(long playerId, Instant exclusiveEnd) {
        return quoteDAO.findLastBefore(playerId, exclusiveEnd).map(quoteMapper::toDomain);
    }

    /**
     * Las cotizaciones del jugador con vigencia desde {@code from} (incluido) hasta {@code to}
     * (excluido), de la más vieja a la más nueva, paginadas. Incluye todas: también las calculadas
     * que una recotización reemplazó.
     */
    @Transactional(readOnly = true)
    public QuotePage<Quote> findHistory(long playerId, Instant from, Instant to, int page, int size) {
        Page<QuoteSQL> rows = quoteDAO.findHistory(playerId, from, to, PageRequest.of(page, size));
        return new QuotePage<>(toQuotes(rows.getContent()), rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

    /**
     * El ranking: la última cotización de cada jugador activo que cumple el filtro, ordenada por
     * valor descendente, nombre e id. Un jugador sin cotización no figura.
     */
    @Transactional(readOnly = true)
    public QuotePage<RankedQuote> findRanking(PlayerFilter filter, int page, int size) {
        Page<QuoteSQL> rows = quoteDAO.findRanking(filter.league(), filter.team(), filter.position(),
                PageRequest.of(page, size));
        List<RankedQuote> ranked = rows.getContent().stream()
                .map(row -> new RankedQuote(playerMapper.toDomain(row.getPlayer()), quoteMapper.toDomain(row)))
                .toList();
        return new QuotePage<>(ranked, rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

    private List<Quote> toQuotes(List<QuoteSQL> rows) {
        return rows.stream().map(quoteMapper::toDomain).toList();
    }
}
