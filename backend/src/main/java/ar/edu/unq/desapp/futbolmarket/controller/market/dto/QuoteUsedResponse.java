package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.StrategyRefResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * La cotización que usó una operación, con su score (esquema {@code QuoteUsed}). Contra la orden
 * inicial también se informa: es la vigente en ese momento (FR-028).
 */
public record QuoteUsedResponse(Instant validFrom, BigDecimal value, BigDecimal tokenPrice, BigDecimal score,
                                StrategyRefResponse strategy) {

    public static QuoteUsedResponse from(Quote quote) {
        return new QuoteUsedResponse(quote.validFrom(), quote.value(), quote.tokenPrice(), quote.score(),
                StrategyRefResponse.from(quote.strategy()));
    }
}
