package ar.edu.unq.desapp.futbolmarket.persistence.repository.player;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class PlayerRepository {
    private final PlayerSQLDAO playerDAO;
    private final PlayerMapper playerMapper;

    public PlayerRepository(PlayerSQLDAO playerDAO, PlayerMapper playerMapper) {
        this.playerDAO = playerDAO;
        this.playerMapper = playerMapper;
    }

    /**
     * El listado muestra solo a los jugadores activos, también al filtrar, y la paginación cuenta
     * solo a ellos (FR-049). {@link #findById} no filtra: el detalle responde para cualquiera.
     */
    @Transactional(readOnly = true)
    public PlayerPage findPage(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var players = playerDAO.findAllByActiveTrueOrderByIdAsc(pageable);
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

    @Transactional
    public Player save(Player player) {
        PlayerSQL savedPlayer = playerDAO.save(playerMapper.toSQL(player));
        return playerMapper.toDomain(savedPlayer);
    }

    @Transactional
    public List<Player> saveAll(List<Player> players) {
        List<PlayerSQL> savedPlayers = playerDAO.saveAll(players.stream().map(playerMapper::toSQL).toList());
        return toPlayers(savedPlayers);
    }

    /**
     * Carga en una sola consulta los jugadores guardados, con su equipo. Sin ids no hay nada que
     * buscar, así que no se consulta la base.
     */
    @Transactional(readOnly = true)
    public List<Player> findAllByExternalIds(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return List.of();
        }
        return toPlayers(playerDAO.findAllByExternalIdIn(externalIds));
    }

    @Transactional(readOnly = true)
    public List<Player> findAllActive() {
        return toPlayers(playerDAO.findAllByActiveTrue());
    }

    @Transactional(readOnly = true)
    public boolean hasPlayers() {
        return playerDAO.count() > 0;
    }

    @Transactional(readOnly = true)
    public Optional<Player> findByExternalId(String externalId) {
        return playerDAO.findByExternalId(externalId).map(playerMapper::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<Player> findById(Long playerId) {
        return playerDAO.findById(playerId).map(playerMapper::toDomain);
    }

    private List<Player> toPlayers(List<PlayerSQL> players) {
        return players.stream().map(playerMapper::toDomain).toList();
    }
}
