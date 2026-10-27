package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * La cotización actual de los jugadores pedidos que tienen una. Los demás no figuran.
 */
public record CurrentQuotesResponse(List<PlayerQuoteResponse> quotes) {

    public static CurrentQuotesResponse from(List<Quote> quotes) {
        return new CurrentQuotesResponse(quotes.stream().map(PlayerQuoteResponse::from).toList());
    }
}
