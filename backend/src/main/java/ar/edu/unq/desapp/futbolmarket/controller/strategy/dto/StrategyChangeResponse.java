package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.RecalculationReportResponse;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyChangeResult;

/**
 * La respuesta de editar o activar una estrategia (esquema {@code StrategyChangeResponse} del
 * contrato): la estrategia después del cambio y el informe de la recotización. {@code requotation}
 * es {@code null} salvo con {@code AHORA} cuando el cambio lo admite; con {@code AHORA} y nada para
 * cotizar es un informe vacío, no {@code null}.
 */
public record StrategyChangeResponse(StrategyResponse strategy, RecalculationReportResponse requotation) {

    public static StrategyChangeResponse from(StrategyChangeResult result) {
        return new StrategyChangeResponse(StrategyResponse.from(result.strategy()),
                result.requotation().map(RecalculationReportResponse::from).orElse(null));
    }
}
