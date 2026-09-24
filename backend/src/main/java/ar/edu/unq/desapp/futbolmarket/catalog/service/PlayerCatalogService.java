package ar.edu.unq.desapp.futbolmarket.catalog.service;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.PlayerRepository;
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
}
