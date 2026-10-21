package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulatedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulationResult;

/**
 * Una hoja de la simulación con la paginación del catálogo. {@code strategy} es la estrategia
 * guardada que se simuló, o {@code null} si fueron parámetros enviados.
 */
public record SimulationPageResponse(StrategyRefResponse strategy, int weeksSimulated,
                                     List<PendingWeekResponse> pendingWeeks, List<SimulationRowResponse> content,
                                     int page, int size, long totalElements, int totalPages, boolean hasPrevious,
                                     boolean hasNext) {

    public static SimulationPageResponse from(SimulationResult result) {
        QuotePage<SimulatedQuote> page = result.page();
        return new SimulationPageResponse(
                result.strategy() == null ? null : StrategyRefResponse.from(result.strategy()),
                result.weeksSimulated(),
                result.pendingWeeks().stream().map(PendingWeekResponse::from).toList(),
                page.content().stream().map(SimulationRowResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }
}
