package ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import org.springframework.stereotype.Component;

/**
 * Traduce una cotización campo a campo. Del jugador solo toma el id (no lo carga); de la versión
 * arma la referencia liviana con su fila y su raíz.
 */
@Component
public class QuoteMapper {

    public Quote toDomain(QuoteSQL quote) {
        return new Quote(
                quote.getId(),
                quote.getPlayer().getId(),
                new QuoteWeek(quote.getWeekStart()),
                quote.getValidFrom(),
                quote.getValue(),
                quote.getBaseValue(),
                quote.getScore(),
                quote.getType(),
                toRef(quote.getStrategy())
        );
    }

    /**
     * {@code player} y {@code strategy} son las referencias de las filas a las que apunta: el
     * repository las obtiene sin cargarlas.
     */
    public QuoteSQL toSQL(Quote quote, PlayerSQL player, StrategySQL strategy) {
        return new QuoteSQL(
                quote.id(),
                player,
                quote.week().monday(),
                quote.validFrom(),
                quote.value(),
                quote.baseValue(),
                quote.score(),
                quote.type(),
                strategy
        );
    }

    public StrategyVersionRef toRef(StrategySQL version) {
        return new StrategyVersionRef(version.getId(), version.strategyId(), version.getName(),
                version.getVersionNumber());
    }
}
