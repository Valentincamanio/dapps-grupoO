package ar.edu.unq.desapp.futbolmarket.persistence.mapper.market;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditEntrySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditPartySQL;

/**
 * Traduce un registro de auditoría campo a campo. Los enums viajan como su nombre, y cada parte
 * como las siete columnas de su prefijo: una segunda parte ausente queda con todas en nulo.
 */
@Component
public class AuditEntryMapper {

    public AuditEntry toDomain(AuditEntrySQL row) {
        return new AuditEntry(
                row.getId(),
                row.getOccurredAt(),
                AuditEventType.valueOf(row.getEventType()),
                AuditActorType.valueOf(row.getActorType()),
                row.getActorId(),
                row.getPlayerId(),
                row.getOrderId(),
                row.getTradeId(),
                OrderSide.valueOf(row.getSide()),
                row.getQuantity(),
                row.getTokenPrice(),
                row.getTotal(),
                row.getQuoteId(),
                row.getCancelReason() == null ? null : CancelReason.valueOf(row.getCancelReason()),
                toChange(row.getPrimary()),
                row.getSecondary() == null ? null : toChange(row.getSecondary()),
                row.getDetail());
    }

    public AuditEntrySQL toSQL(AuditEntry entry) {
        return new AuditEntrySQL(
                entry.id(),
                entry.occurredAt(),
                entry.eventType().name(),
                entry.actorType().name(),
                entry.actorId(),
                entry.playerId(),
                entry.orderId(),
                entry.tradeId(),
                entry.side().name(),
                entry.quantity(),
                entry.tokenPrice(),
                entry.total(),
                entry.quoteId(),
                entry.cancelReason() == null ? null : entry.cancelReason().name(),
                toParty(entry.primary()),
                entry.secondary() == null ? null : toParty(entry.secondary()),
                entry.detail());
    }

    private static PartyChange toChange(AuditPartySQL party) {
        return new PartyChange(
                new PartyState(party.getUserId(), party.getFreeBefore(), party.getReservedBefore(),
                        party.getTokensBefore()),
                new PartyState(party.getUserId(), party.getFreeAfter(), party.getReservedAfter(),
                        party.getTokensAfter()));
    }

    private static AuditPartySQL toParty(PartyChange change) {
        return new AuditPartySQL(change.userId(), change.before().freeBalance(), change.after().freeBalance(),
                change.before().reservedBalance(), change.after().reservedBalance(), change.before().tokens(),
                change.after().tokens());
    }
}
