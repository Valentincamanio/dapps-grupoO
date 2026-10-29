package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.controller.market.dto.AuditPartyResponse.Role;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;

/**
 * Un registro de la auditoría (esquema {@code AuditEntry}). Los campos que no corresponden al tipo
 * de hecho van en {@code null}, como en el contrato. Las partes van en una lista: el dueño en una
 * orden creada o cancelada, y el comprador y el vendedor, en ese orden, en una operación.
 */
public record AuditEntryResponse(Long id, Instant occurredAt, AuditEventType eventType, AuditActorType actorType,
                                 Long actorId, long playerId, Long orderId, Long tradeId, OrderSide side,
                                 int quantity, BigDecimal tokenPrice, BigDecimal total, Long quoteId,
                                 CancelReason cancelReason, String detail, List<AuditPartyResponse> parties) {

    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.id(), entry.occurredAt(), entry.eventType(), entry.actorType(),
                entry.actorId(), entry.playerId(), entry.orderId(), entry.tradeId(), entry.side(), entry.quantity(),
                entry.tokenPrice(), entry.total(), entry.quoteId(), entry.cancelReason(), entry.detail(),
                partiesOf(entry));
    }

    private static List<AuditPartyResponse> partiesOf(AuditEntry entry) {
        if (entry.secondary() == null) {
            return List.of(AuditPartyResponse.from(Role.OWNER, entry.primary()));
        }
        return List.of(AuditPartyResponse.from(Role.BUYER, entry.primary()),
                AuditPartyResponse.from(Role.SELLER, entry.secondary()));
    }
}
