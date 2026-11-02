package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;

/**
 * Una cotización, con los campos del esquema {@code Quote} del contrato. Los importes salen con su
 * escala ({@code 103.00}, {@code 1.0300}) y el score con sus seis decimales ({@code 0.300000}).
 * {@code variation} no aparece en la {@code INITIAL}.
 *
 * <p>Desde la 1.1.0 del contrato lleva el score con que se calculó (FR-058 de la 008); a propósito
 * sigue sin llevar el valor base ni estadísticas (FR-006).</p>
 */
public record QuoteResponse(BigDecimal value, BigDecimal tokenPrice, BigDecimal score,
                            @JsonInclude(JsonInclude.Include.NON_NULL) VariationResponse variation,
                            LocalDate weekStart, Instant validFrom, QuoteType type, StrategyRefResponse strategy) {

    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.value(),
                quote.tokenPrice(),
                quote.score(),
                quote.variation().map(VariationResponse::from).orElse(null),
                quote.week().monday(),
                quote.validFrom(),
                quote.type(),
                StrategyRefResponse.from(quote.strategy()));
    }
}
