package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.unq.desapp.futbolmarket.modelo.market.PortfolioTotals;

/**
 * Los totales del portfolio (esquema {@code PortfolioTotals}). El saldo libre y el reservado solo
 * viajan en el portfolio actual (FR-040); la ganancia realizada viaja nula en el del superusuario.
 */
public record PortfolioTotalsResponse(BigDecimal balance,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal availableBalance,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal reservedBalance,
                                      BigDecimal invested, BigDecimal value, BigDecimal profit,
                                      BigDecimal realizedProfit, BigDecimal equity) {

    public static PortfolioTotalsResponse from(PortfolioTotals totals) {
        return new PortfolioTotalsResponse(totals.balance(), totals.availableBalance(), totals.reservedBalance(),
                totals.invested(), totals.value(), totals.profit(), totals.realizedProfit(), totals.equity());
    }
}
