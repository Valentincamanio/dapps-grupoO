package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;

/**
 * Una hoja de la auditoría (esquema {@code AuditPage}), con la paginación del catálogo.
 */
public record AuditPageResponse(List<AuditEntryResponse> content, int page, int size, long totalElements,
                                int totalPages, boolean hasPrevious, boolean hasNext) {

    public static AuditPageResponse from(QuotePage<AuditEntry> entries) {
        return new AuditPageResponse(
                entries.content().stream().map(AuditEntryResponse::from).toList(),
                entries.page(),
                entries.size(),
                entries.totalElements(),
                entries.totalPages(),
                entries.hasPrevious(),
                entries.hasNext());
    }
}
