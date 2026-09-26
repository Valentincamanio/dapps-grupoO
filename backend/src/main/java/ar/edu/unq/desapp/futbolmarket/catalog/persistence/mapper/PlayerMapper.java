package ar.edu.unq.desapp.futbolmarket.catalog.persistence.mapper;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.entity.PlayerSQL;
import org.springframework.stereotype.Component;

@Component
public class PlayerMapper {
    private final TeamMapper teamMapper;

    public PlayerMapper(TeamMapper teamMapper) {
        this.teamMapper = teamMapper;
    }

    public Player toDomain(PlayerSQL player) {
        return new Player(
                player.getId(),
                player.getExternalId(),
                player.getName(),
                player.getPosition(),
                teamMapper.toDomain(player.getTeam())
        );
    }

    public PlayerSQL toSQL(Player player) {
        return new PlayerSQL(
                player.id(),
                player.externalId(),
                player.name(),
                player.position(),
                teamMapper.toSQL(player.team())
        );
    }
}
