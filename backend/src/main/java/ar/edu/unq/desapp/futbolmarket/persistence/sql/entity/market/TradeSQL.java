package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una operación ejecutada (data-model 6.3). La tabla no se llama {@code transaction}, que es
 * palabra reservada.
 *
 * <p>Una operación guardada no se modifica nunca (SC-011): la entidad es {@code @Immutable}, no
 * tiene setters y ninguna columna es actualizable. La cotización también es inmutable (007), así
 * que la operación puede apuntar a la suya.</p>
 */
@Entity
@Immutable
@Table(name = "market_trades", indexes = {
        @Index(name = "ix_market_trades_buyer_executed", columnList = "buyer_id, executed_at DESC"),
        @Index(name = "ix_market_trades_seller_executed", columnList = "seller_id, executed_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TradeSQL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerSQL player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_order_id", nullable = false, updatable = false)
    private MarketOrderSQL buyOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_order_id", nullable = false, updatable = false)
    private MarketOrderSQL sellOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false, updatable = false)
    private AppUserSQL buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private AppUserSQL seller;

    @Column(name = "seller_type", nullable = false, updatable = false, length = MarketColumns.OWNER_TYPE_LENGTH)
    private String sellerType;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "token_price", nullable = false, updatable = false, precision = MarketColumns.PRICE_PRECISION,
            scale = MarketColumns.PRICE_SCALE)
    private BigDecimal tokenPrice;

    @Column(nullable = false, updatable = false, precision = MarketColumns.CREDIT_PRECISION,
            scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private QuoteSQL quote;

    @Column(name = "buyer_balance_before", nullable = false, updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal buyerBalanceBefore;

    @Column(name = "buyer_balance_after", nullable = false, updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal buyerBalanceAfter;

    @Column(name = "seller_balance_before", nullable = false, updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal sellerBalanceBefore;

    @Column(name = "seller_balance_after", nullable = false, updatable = false,
            precision = MarketColumns.CREDIT_PRECISION, scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal sellerBalanceAfter;

    @Column(name = "buyer_tokens_before", nullable = false, updatable = false)
    private int buyerTokensBefore;

    @Column(name = "buyer_tokens_after", nullable = false, updatable = false)
    private int buyerTokensAfter;

    @Column(name = "seller_tokens_before", nullable = false, updatable = false)
    private int sellerTokensBefore;

    @Column(name = "seller_tokens_after", nullable = false, updatable = false)
    private int sellerTokensAfter;

    /** Nula cuando vende el superusuario, que no compró (research D12). */
    @Column(name = "realized_profit", updatable = false, precision = MarketColumns.CREDIT_PRECISION,
            scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal realizedProfit;
}
