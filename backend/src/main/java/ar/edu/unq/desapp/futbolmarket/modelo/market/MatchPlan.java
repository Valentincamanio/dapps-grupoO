package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que decide {@link OrderMatcher} para un pedido (research D10): los cruces, en el orden en que
 * se ejecutan, y lo que queda pendiente de la orden entrante.
 */
public record MatchPlan(List<Fill> fills, int remainder) {

    public MatchPlan {
        fills = List.copyOf(fills);
    }

    public int executedQuantity() {
        return fills.stream().mapToInt(Fill::quantity).sum();
    }

    public BigDecimal executedTotal() {
        return fills.stream().map(Fill::total).reduce(MarketMath.ZERO_CREDITS, BigDecimal::add);
    }
}
