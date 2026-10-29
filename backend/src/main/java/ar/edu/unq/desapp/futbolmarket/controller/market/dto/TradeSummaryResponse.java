package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * Una operación vista por una de sus partes (esquema {@code TradeSummary}): la contraparte se dice
 * por su tipo, nunca por quién es.
 */
public record TradeSummaryResponse(Long id, Instant executedAt, int quantity, BigDecimal tokenPrice, BigDecimal total,
                                   OwnerType counterparty, QuoteUsedResponse quote) {

    public static TradeSummaryResponse from(Trade trade, long userId, Quote quote) {
        return new TradeSummaryResponse(trade.id(), trade.executedAt(), trade.quantity(), trade.tokenPrice(),
                trade.total(), trade.counterpartyFor(userId), QuoteUsedResponse.from(quote));
    }
}
