package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * Lo que vale la tenencia de un jugador con una cotización (data-model 2.11). Se calcula, no se
 * guarda. Se llama así porque {@code Position} ya es la posición del jugador en la cancha (research
 * D3).
 *
 * <p>El precio promedio, lo invertido, la ganancia y la rentabilidad son nulos en las posiciones
 * del superusuario, que no compró (research D7). Los comprometidos son nulos en el portfolio a una
 * fecha (FR-040).</p>
 *
 * @param tokens        los tokens que tiene, siempre más de 0
 * @param committed     los tokens en sus ventas pendientes
 * @param quote         la cotización con la que se valúa
 * @param value         {@code total(tokens, tokenPrice)}
 * @param profit        valor − invertido
 * @param profitPercent la ganancia sobre lo invertido; nula si lo invertido es 0
 */
public record PortfolioPosition(Player player, int tokens, Integer committed, BigDecimal averagePrice,
                                BigDecimal invested, Quote quote, BigDecimal value, BigDecimal profit,
                                BigDecimal profitPercent) {

    /**
     * La posición de un usuario: su costo sale de lo que pagó (FR-037).
     */
    public static PortfolioPosition held(Player player, CostBasis basis, Integer committed, Quote quote) {
        BigDecimal value = MarketMath.total(basis.quantity(), quote.tokenPrice());
        BigDecimal invested = basis.invested();
        BigDecimal profit = value.subtract(invested);
        return new PortfolioPosition(player, basis.quantity(), committed, basis.averagePrice(), invested, quote, value,
                profit, MarketMath.percent(profit, invested).orElse(null));
    }

    /**
     * Los tokens sin vender del superusuario: todos están a la venta en su orden inicial, y no
     * tienen costo.
     */
    public static PortfolioPosition unsold(Player player, int tokens, Quote quote) {
        return new PortfolioPosition(player, tokens, tokens, null, null, quote,
                MarketMath.total(tokens, quote.tokenPrice()), null, null);
    }

    /**
     * Cada jugador tiene 100 tokens: cada token es el 1 % del jugador.
     */
    public int sharePercent() {
        return tokens;
    }
}
