package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Una hoja de resultados de cotizaciones, con la misma regla de paginación que
 * {@code PlayerPage}.
 */
public record QuotePage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
    public QuotePage(List<T> content, int page, int size, long totalElements) {
        this(
                List.copyOf(content),
                page,
                size,
                totalElements,
                calculateTotalPages(size, totalElements),
                page > 0,
                page + 1 < calculateTotalPages(size, totalElements)
        );
    }

    private static int calculateTotalPages(int size, long totalElements) {
        if (size <= 0) {
            throw new QuoteInvariantException("El tamaño de página debe ser mayor que cero.");
        }
        if (totalElements < 0) {
            throw new QuoteInvariantException("El total de elementos no puede ser negativo.");
        }
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}
