package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un registro de auditoría (data-model 6.4). No se modifica ni se borra (FR-051): la entidad es
 * {@code @Immutable}, no tiene setters y ninguna columna es actualizable.
 *
 * <p>Los ids son columnas simples, sin claves foráneas: un registro describe un hecho y no tiene
 * que impedir nada en las demás tablas, y el autor puede ser el sistema. Las dos partes son
 * {@code @Embedded} con prefijo propio en vez de otra tabla: con dos como máximo alcanza, y se
 * filtra por usuario sin {@code join} (research D17).</p>
 */
@Entity
@Immutable
@Table(name = "audit_entries", indexes = {
        @Index(name = "ix_audit_entries_occurred", columnList = "occurred_at DESC"),
        @Index(name = "ix_audit_entries_primary_user", columnList = "primary_user_id, occurred_at"),
        @Index(name = "ix_audit_entries_secondary_user", columnList = "secondary_user_id, occurred_at"),
        @Index(name = "ix_audit_entries_player", columnList = "player_id, occurred_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditEntrySQL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, updatable = false, length = MarketColumns.EVENT_TYPE_LENGTH)
    private String eventType;

    @Column(name = "actor_type", nullable = false, updatable = false, length = MarketColumns.ACTOR_TYPE_LENGTH)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "trade_id", updatable = false)
    private Long tradeId;

    @Column(nullable = false, updatable = false, length = MarketColumns.SIDE_LENGTH)
    private String side;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "token_price", updatable = false, precision = MarketColumns.PRICE_PRECISION,
            scale = MarketColumns.PRICE_SCALE)
    private BigDecimal tokenPrice;

    @Column(updatable = false, precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal total;

    @Column(name = "quote_id", updatable = false)
    private Long quoteId;

    @Column(name = "cancel_reason", updatable = false, length = MarketColumns.CANCEL_REASON_LENGTH)
    private String cancelReason;

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "primary_user_id", nullable = false, updatable = false))
    @AttributeOverride(name = "freeBefore", column = @Column(name = "primary_free_before", nullable = false,
            updatable = false, precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "freeAfter", column = @Column(name = "primary_free_after", nullable = false,
            updatable = false, precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "reservedBefore", column = @Column(name = "primary_reserved_before", nullable = false,
            updatable = false, precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "reservedAfter", column = @Column(name = "primary_reserved_after", nullable = false,
            updatable = false, precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "tokensBefore", column = @Column(name = "primary_tokens_before", nullable = false,
            updatable = false))
    @AttributeOverride(name = "tokensAfter", column = @Column(name = "primary_tokens_after", nullable = false,
            updatable = false))
    private AuditPartySQL primary;

    /** Solo en una operación: el vendedor. En los demás hechos, todas sus columnas quedan nulas. */
    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "secondary_user_id", updatable = false))
    @AttributeOverride(name = "freeBefore", column = @Column(name = "secondary_free_before", updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "freeAfter", column = @Column(name = "secondary_free_after", updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "reservedBefore", column = @Column(name = "secondary_reserved_before", updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "reservedAfter", column = @Column(name = "secondary_reserved_after", updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE))
    @AttributeOverride(name = "tokensBefore", column = @Column(name = "secondary_tokens_before", updatable = false))
    @AttributeOverride(name = "tokensAfter", column = @Column(name = "secondary_tokens_after", updatable = false))
    private AuditPartySQL secondary;

    @Column(nullable = false, updatable = false, length = AuditEntry.MAX_DETAIL_LENGTH)
    private String detail;
}
