package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.RecalculationReportResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;

/**
 * La respuesta de editar o activar una estrategia (esquema {@code StrategyChangeResponse} del
 * contrato): la estrategia después del cambio y el informe de la recotización. Hasta que exista la
 * opción {@code AHORA} (US5) el cambio rige desde la semana siguiente y {@code requotation} es
 * siempre {@code null}.
 */
public record StrategyChangeResponse(StrategyResponse strategy, RecalculationReportResponse requotation) {

    public static StrategyChangeResponse withoutRequotation(StrategyDetail detail) {
        return new StrategyChangeResponse(StrategyResponse.from(detail), null);
    }
}
