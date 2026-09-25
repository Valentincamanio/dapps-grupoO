package ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.mapper.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.PlayerSQLDAO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class PlayerRepository {
    private final PlayerSQLDAO playerDAO;
    private final PlayerMapper playerMapper;

    public PlayerRepository(PlayerSQLDAO playerDAO, PlayerMapper playerMapper) {
        this.playerDAO = playerDAO;
        this.playerMapper = playerMapper;
    }

    @Transactional(readOnly = true)
    public PlayerPage findPage(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var players = playerDAO.findAllByOrderByIdAsc(pageable);
        return toPlayerPage(players);
    }

    @Transactional(readOnly = true)
    public PlayerPage findPage(int page, int size, PlayerFilter filter) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var players = playerDAO.findAllByFilters(filter.league(), filter.team(), filter.position(), pageable);
        return toPlayerPage(players);
    }

    private PlayerPage toPlayerPage(org.springframework.data.domain.Page<PlayerSQL> players) {
        return new PlayerPage(
                players.getContent().stream().map(playerMapper::toDomain).toList(),
                players.getNumber(),
                players.getSize(),
                players.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public boolean existsByExternalId(String externalId) {
        return playerDAO.findByExternalId(externalId).isPresent();
    }

    @Transactional
    public Player save(Player player) {
        PlayerSQL savedPlayer = playerDAO.save(playerMapper.toSQL(player));
        return playerMapper.toDomain(savedPlayer);
    }

    @Transactional(readOnly = true)
    public Optional<Player> findByExternalId(String externalId) {
        return playerDAO.findByExternalId(externalId).map(playerMapper::toDomain);
    }
}
