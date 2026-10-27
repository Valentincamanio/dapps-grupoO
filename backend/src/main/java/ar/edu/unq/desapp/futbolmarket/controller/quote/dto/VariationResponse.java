package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteVariation;

/**
 * La variación de una cotización contra su valor base: en créditos y en porcentaje, con dos
 * decimales y signo.
 */
public record VariationResponse(BigDecimal amount, BigDecimal percent) {

    public static VariationResponse from(QuoteVariation variation) {
        return new VariationResponse(variation.amount(), variation.percent());
    }
}
