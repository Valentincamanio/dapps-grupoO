package ar.edu.unq.desapp.futbolmarket.catalog.controller;

import ar.edu.unq.desapp.futbolmarket.catalog.controller.dto.PlayerPageResponse;
import ar.edu.unq.desapp.futbolmarket.catalog.controller.dto.PlayerResponse;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.service.PlayerCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/players")
@Validated
public class PlayerController {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private final PlayerCatalogService playerCatalogService;

    public PlayerController(PlayerCatalogService playerCatalogService) {
        this.playerCatalogService = playerCatalogService;
    }

    @GetMapping
    public PlayerPageResponse getPlayers(
            @RequestParam(defaultValue = "0") @Min(DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) League league,
            @RequestParam(required = false) @Pattern(regexp = ".*\\S.*") String team,
            @RequestParam(required = false) Position position
    ) {
        var filter = new PlayerFilter(league, team, position);
        var playerPage = filter.hasCriteria()
                ? playerCatalogService.getPlayers(page, size, filter)
                : playerCatalogService.getPlayers(page, size);
        return toResponse(playerPage);
    }

    @GetMapping("/{id}")
    public PlayerResponse getPlayer(@PathVariable @Min(1) Long id) {
        return toResponse(playerCatalogService.getPlayer(id));
    }

    private PlayerPageResponse toResponse(PlayerPage playerPage) {
        return new PlayerPageResponse(
                playerPage.content().stream().map(this::toResponse).toList(),
                playerPage.page(),
                playerPage.size(),
                playerPage.totalElements(),
                playerPage.totalPages(),
                playerPage.hasPrevious(),
                playerPage.hasNext()
        );
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(player.id(), player.name(), player.position(), player.team().name(), player.league());
    }
}
