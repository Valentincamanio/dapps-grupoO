package ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.BoostSQL;

/**
 * Traduce las filas de {@code boosts} de una semana a sus potenciados, campo a campo. El orden en la
 * semana es el de la lista.
 */
@Component
public class BoostMapper {

    public WeekBoosts toDomain(QuoteWeek week, List<BoostSQL> rows) {
        return new WeekBoosts(week, rows.stream()
                .map(row -> new PlayerBoost(row.getPlayer().getId(), row.getPercent()))
                .toList());
    }

    public BoostSQL toSQL(QuoteWeek week, PlayerBoost boost, PlayerSQL player, int positionInWeek) {
        return new BoostSQL(week.monday(), player, boost.percent(), positionInWeek);
    }
}
