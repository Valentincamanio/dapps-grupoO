package ar.edu.unq.desapp.futbolmarket.service.player;

import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import org.springframework.stereotype.Service;

@Service
public class PlayerCatalogService {
    private final PlayerRepository playerRepository;

    public PlayerCatalogService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public PlayerPage getPlayers(int page, int size) {
        return playerRepository.findPage(page, size);
    }

    public PlayerPage getPlayers(int page, int size, PlayerFilter filter) {
        return playerRepository.findPage(page, size, filter);
    }

    public Player getPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
    }
}
