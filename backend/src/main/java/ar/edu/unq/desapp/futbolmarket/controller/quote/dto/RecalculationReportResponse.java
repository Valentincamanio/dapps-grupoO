package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;

/**
 * El informe de un recálculo, con los campos y los nombres del esquema {@code RecalculationReport}
 * del contrato. {@code interruptedAt} es {@code null} si la corrida no se cortó.
 */
public record RecalculationReportResponse(RecalculationOrigin origin, Instant startedAt, Instant finishedAt,
                                          StrategyRefResponse strategy, List<WeekResultResponse> quotedWeeks,
                                          List<PendingWeekResponse> pendingWeeks, boolean nothingToQuote,
                                          LocalDate interruptedAt) {

    public static RecalculationReportResponse from(RecalculationReport report) {
        return new RecalculationReportResponse(
                report.origin(),
                report.startedAt(),
                report.finishedAt(),
                StrategyRefResponse.from(report.strategy()),
                report.quotedWeeks().stream().map(WeekResultResponse::from).toList(),
                report.pendingWeeks().stream().map(PendingWeekResponse::from).toList(),
                report.isEmpty(),
                report.interruptedAt().map(QuoteWeek::monday).orElse(null));
    }
}
