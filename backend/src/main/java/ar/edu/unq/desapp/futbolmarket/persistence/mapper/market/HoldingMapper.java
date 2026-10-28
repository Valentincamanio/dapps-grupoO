package ar.edu.unq.desapp.futbolmarket.persistence.mapper.market;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.CostBasis;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Holding;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.HoldingSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;

/**
 * Traduce una tenencia campo a campo, con su {@link CostBasis}. Del titular y del jugador solo toma
 * el id, sin cargarlos.
 */
@Component
public class HoldingMapper {

    public Holding toDomain(HoldingSQL row) {
        CostBasis basis = new CostBasis(row.getQuantity(), row.getAcquiredQuantity(), row.getAcquiredCost());
        return Holding.reconstitute(row.getId(), row.getOwner().getId(), row.getPlayer().getId(), basis,
                row.getCommittedQuantity());
    }

    /** Si el id viene en {@code null}, JPA inserta; si no, actualiza. */
    public HoldingSQL toSQL(Holding holding, AppUserSQL owner, PlayerSQL player) {
        HoldingSQL row = new HoldingSQL();
        row.setId(holding.getId());
        row.setOwner(owner);
        row.setPlayer(player);
        row.setQuantity(holding.getBasis().quantity());
        row.setCommittedQuantity(holding.getCommitted());
        row.setAcquiredQuantity(holding.getBasis().acquiredQuantity());
        row.setAcquiredCost(holding.getBasis().acquiredCost());
        return row;
    }
}
