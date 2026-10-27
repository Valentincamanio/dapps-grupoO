package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una cotización guardada (research D15). No tiene setters ni columnas actualizables: una
 * cotización no cambia nunca (FR-005).
 *
 * <p>{@code strategy_id} apunta a la fila de la versión que la calculó. Las columnas se llaman
 * {@code quote_value} y {@code quote_type} porque {@code value} es palabra reservada en H2 2.x. El
 * índice descendente lo pidió el equipo aunque el único ya sirve en los dos sentidos.</p>
 */
@Entity
@Table(name = "quotes", indexes = {
        @Index(name = "ux_quotes_player_valid_from", columnList = "player_id, valid_from", unique = true),
        @Index(name = "ix_quotes_player_valid_from_desc", columnList = "player_id, valid_from DESC"),
        @Index(name = "ix_quotes_week_start_type", columnList = "week_start, quote_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuoteSQL {
    private static final int VALUE_PRECISION = 12;
    private static final int VALUE_SCALE = 2;
    private static final int SCORE_PRECISION = 10;
    private static final int SCORE_SCALE = 6;
    private static final int TYPE_LENGTH = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerSQL player;

    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "quote_value", nullable = false, updatable = false, precision = VALUE_PRECISION, scale = VALUE_SCALE)
    private BigDecimal value;

    @Column(name = "base_value", updatable = false, precision = VALUE_PRECISION, scale = VALUE_SCALE)
    private BigDecimal baseValue;

    @Column(nullable = false, updatable = false, precision = SCORE_PRECISION, scale = SCORE_SCALE)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_type", nullable = false, updatable = false, length = TYPE_LENGTH)
    private QuoteType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategySQL strategy;
}
