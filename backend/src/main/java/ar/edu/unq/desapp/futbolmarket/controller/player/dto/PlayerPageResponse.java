package ar.edu.unq.desapp.futbolmarket.controller.player.dto;

import java.util.List;

public record PlayerPageResponse(
        List<PlayerResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
