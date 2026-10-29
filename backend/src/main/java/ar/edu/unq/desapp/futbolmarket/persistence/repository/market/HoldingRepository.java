package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.Holding;
import ar.edu.unq.desapp.futbolmarket.modelo.market.HoldingView;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.market.HoldingMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.HoldingSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.HoldingSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Guarda y lee las tenencias de los usuarios: recibe y devuelve {@link Holding}. Las lecturas corren
 * en la transacción de quien las pide (research D13).
 */
@Repository
@RequiredArgsConstructor
public class HoldingRepository {
    private final HoldingSQLDAO holdingDAO;
    private final AppUserSQLDAO appUserDAO;
    private final PlayerSQLDAO playerDAO;
    private final HoldingMapper mapper;
    private final PlayerMapper playerMapper;

    /**
     * Las tenencias con tokens del titular, cada una con su jugador: las posiciones de su portfolio
     * actual, en una sola consulta.
     */
    @Transactional(readOnly = true)
    public List<HoldingView> findPositionsOf(long ownerId) {
        return holdingDAO.findPositions(ownerId).stream()
                .map(row -> new HoldingView(mapper.toDomain(row), playerMapper.toDomain(row.getPlayer())))
                .toList();
    }

    /**
     * Las tenencias del jugador de esos titulares: las del libro de un pedido. Sin titulares no se
     * consulta la base.
     */
    public List<Holding> findOfPlayer(long playerId, Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        return holdingDAO.findByPlayerIdAndOwnerIdIn(playerId, ownerIds).stream().map(mapper::toDomain).toList();
    }

    /**
     * Inserta las nuevas y actualiza las existentes. El titular y el jugador se referencian sin
     * cargarlos.
     */
    @Transactional
    public List<Holding> saveAll(Collection<Holding> holdings) {
        List<HoldingSQL> rows = holdings.stream()
                .map(holding -> mapper.toSQL(holding, appUserDAO.getReferenceById(holding.getOwnerId()),
                        playerDAO.getReferenceById(holding.getPlayerId())))
                .toList();
        return holdingDAO.saveAll(rows).stream().map(mapper::toDomain).toList();
    }
}
