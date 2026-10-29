package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.time.LocalDate;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;

/**
 * El portfolio de una cuenta (esquema {@code Portfolio}). {@code date} viaja nula en el actual.
 */
public record PortfolioResponse(Long userId, LocalDate date, List<PortfolioPositionResponse> positions,
                                PortfolioTotalsResponse totals) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.userId(), portfolio.date(),
                portfolio.positions().stream().map(PortfolioPositionResponse::from).toList(),
                PortfolioTotalsResponse.from(portfolio.totals()));
    }
}
