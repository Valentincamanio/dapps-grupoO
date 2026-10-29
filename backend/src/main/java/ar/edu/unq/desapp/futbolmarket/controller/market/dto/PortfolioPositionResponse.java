package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PortfolioPosition;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

/**
 * Una posición del portfolio (esquema {@code PortfolioPosition}). Los comprometidos solo viajan en
 * el portfolio actual; el precio promedio, lo invertido, la ganancia y la rentabilidad viajan nulos
 * en el del superusuario, que no compró.
 */
public record PortfolioPositionResponse(PositionPlayerResponse player, int tokens, int sharePercent,
                                        @JsonInclude(JsonInclude.Include.NON_NULL) Integer committed,
                                        BigDecimal averagePrice, BigDecimal invested, QuoteUsedResponse quote,
                                        BigDecimal value, BigDecimal profit, BigDecimal profitPercent) {

    public static PortfolioPositionResponse from(PortfolioPosition position) {
        return new PortfolioPositionResponse(PositionPlayerResponse.from(position.player()), position.tokens(),
                position.sharePercent(), position.committed(), position.averagePrice(), position.invested(),
                QuoteUsedResponse.from(position.quote()), position.value(), position.profit(),
                position.profitPercent());
    }

    /**
     * El jugador de una posición (esquema {@code PositionPlayer}): el de {@code PlayerRef} con su
     * posición y si sigue activo.
     */
    public record PositionPlayerResponse(Long id, String name, String team, League league, Position position,
                                         boolean active) {

        public static PositionPlayerResponse from(Player player) {
            return new PositionPlayerResponse(player.id(), player.name(), player.team().name(), player.league(),
                    player.position(), player.active());
        }
    }
}
