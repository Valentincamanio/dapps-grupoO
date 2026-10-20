package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;

/**
 * Una cotización con el id de su jugador, en el mismo nivel que el resto de los campos (esquema
 * {@code PlayerQuote} del contrato): la usan la cotización actual, la de una fecha y el lote de la
 * pizarra.
 */
public record PlayerQuoteResponse(long playerId, BigDecimal value, BigDecimal tokenPrice,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) VariationResponse variation,
                                  LocalDate weekStart, Instant validFrom, QuoteType type,
                                  StrategyRefResponse strategy) {

    public static PlayerQuoteResponse from(Quote quote) {
        QuoteResponse response = QuoteResponse.from(quote);
        return new PlayerQuoteResponse(quote.playerId(), response.value(), response.tokenPrice(),
                response.variation(), response.weekStart(), response.validFrom(), response.type(),
                response.strategy());
    }
}
