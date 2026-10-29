package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que decide {@link OrderMatcher} para un pedido (research D10): los cruces, en el orden en que
 * se ejecutan, lo que queda pendiente de la orden entrante y, en una venta, lo que las compras
 * pendientes no pueden pagar (research D11). Una compra nunca tiene faltantes.
 */
public record MatchPlan(List<Fill> fills, int remainder, List<Shortfall> shortfalls) {

    public MatchPlan {
        fills = List.copyOf(fills);
        shortfalls = List.copyOf(shortfalls);
    }

    public MatchPlan(List<Fill> fills, int remainder) {
        this(fills, remainder, List.of());
    }

    public int executedQuantity() {
        return fills.stream().mapToInt(Fill::quantity).sum();
    }

    public BigDecimal executedTotal() {
        return fills.stream().map(Fill::total).reduce(MarketMath.ZERO_CREDITS, BigDecimal::add);
    }
}
