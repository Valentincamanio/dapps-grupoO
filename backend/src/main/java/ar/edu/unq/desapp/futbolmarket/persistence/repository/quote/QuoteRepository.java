package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.QuoteMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.QuoteSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public QuoteRepository(QuoteSQLDAO quoteDAO, PlayerSQLDAO playerDAO, StrategySQLDAO strategyDAO,
                           QuoteMapper quoteMapper) {
        this.quoteDAO = quoteDAO;
        this.playerDAO = playerDAO;
        this.strategyDAO = strategyDAO;
        this.quoteMapper = quoteMapper;
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

    private List<Quote> toQuotes(List<QuoteSQL> rows) {
        return rows.stream().map(quoteMapper::toDomain).toList();
    }
}
