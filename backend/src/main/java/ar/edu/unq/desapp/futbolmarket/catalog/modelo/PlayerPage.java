package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

import java.util.List;

public record PlayerPage(
        List<Player> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
    public PlayerPage(List<Player> content, int page, int size, long totalElements) {
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
            throw new CatalogInvariantException("El tamaño de página debe ser mayor que cero.");
        }
        if (totalElements < 0) {
            throw new CatalogInvariantException("El total de elementos no puede ser negativo.");
        }
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}
