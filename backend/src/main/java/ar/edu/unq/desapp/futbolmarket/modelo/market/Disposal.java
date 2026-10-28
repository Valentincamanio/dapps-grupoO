package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

/**
 * El resultado de vender tokens de una tenencia: lo que queda y la ganancia (o pérdida) realizada.
 */
public record Disposal(CostBasis basis, BigDecimal realizedProfit) {
}
