package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import java.math.BigDecimal;
import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.user.AppUserSQL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una orden de compra o de venta (data-model 6.1). La tabla no se llama {@code order}, que es
 * palabra reservada.
 *
 * <p>{@code status} es una copia del estado que el modelo deriva de las cantidades: se guarda para
 * filtrar el libro y el listado sin calcularlo en la consulta. {@code initial_player_id} es igual al
 * jugador en la orden inicial y nulo en las demás; su índice único deja una sola inicial por
 * jugador sin un índice parcial, porque H2 y PostgreSQL admiten varios nulos (research D8).</p>
 */
@Entity
@Table(name = "market_orders", indexes = {
        @Index(name = "ix_market_orders_book", columnList = "player_id, side, status, created_at"),
        @Index(name = "ix_market_orders_status_created", columnList = "status, created_at"),
        @Index(name = "ix_market_orders_owner_created", columnList = "owner_id, created_at DESC"),
        @Index(name = "ux_market_orders_initial_player", columnList = "initial_player_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class MarketOrderSQL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = MarketColumns.SIDE_LENGTH)
    private String side;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private AppUserSQL owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerSQL player;

    @Column(name = "requested_quantity", nullable = false, updatable = false)
    private int requestedQuantity;

    @Column(name = "executed_quantity", nullable = false)
    private int executedQuantity;

    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity;

    @Column(nullable = false, length = MarketColumns.STATUS_LENGTH)
    private String status;

    @Column(name = "reserved_credits", nullable = false, precision = MarketColumns.CREDIT_PRECISION,
            scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal reservedCredits;

    @Column(name = "reservation_price", updatable = false, precision = MarketColumns.PRICE_PRECISION,
            scale = MarketColumns.PRICE_SCALE)
    private BigDecimal reservationPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "initial_player_id", updatable = false)
    private Long initialPlayerId;

    @Column(name = "cancel_reason", length = MarketColumns.CANCEL_REASON_LENGTH)
    private String cancelReason;
}
