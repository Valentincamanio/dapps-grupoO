package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;

/**
 * Los filtros del listado del mercado (FR-047), opcionales y combinables: un filtro nulo no filtra.
 *
 * <p>El equipo se normaliza con la misma regla del catálogo: se recortan y colapsan los espacios, y
 * uno vacío es {@code CatalogInvariantException} (400). Se aplica a través de {@link PlayerFilter}
 * para no repetir la regla ni su mensaje (data-model 12.2).</p>
 */
public record MarketOrderFilter(OrderSide side, League league, String team, OwnerType ownerType, Long playerId) {

    public MarketOrderFilter {
        team = new PlayerFilter(league, team, null).team();
    }

    /**
     * Sin filtros: todas las órdenes con tokens pendientes.
     */
    public static MarketOrderFilter none() {
        return new MarketOrderFilter(null, null, null, null, null);
    }
}
