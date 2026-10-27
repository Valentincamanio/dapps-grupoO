package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy;

import java.math.BigDecimal;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un peso de una versión. {@code weight_set} es {@code DEFAULT} o el nombre de una posición, y es
 * {@code NOT NULL} para que el índice único funcione (un {@code NULL} no choca consigo mismo). El
 * juego y la métrica son texto; la traducción la hace {@code StrategyMapper}. Solo se guardan los
 * pesos mayores que 0.
 */
@Entity
@Table(name = "strategy_weights",
        indexes = @Index(name = "ux_strategy_weights_set_metric", columnList = "strategy_id, weight_set, metric",
                unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategyWeightSQL {
    private static final int WEIGHT_SET_LENGTH = 20;
    private static final int METRIC_LENGTH = 30;
    private static final int WEIGHT_PRECISION = 3;
    private static final int WEIGHT_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
    private StrategySQL strategy;

    @Column(name = "weight_set", nullable = false, length = WEIGHT_SET_LENGTH, updatable = false)
    private String weightSet;

    @Column(nullable = false, length = METRIC_LENGTH, updatable = false)
    private String metric;

    @Column(nullable = false, precision = WEIGHT_PRECISION, scale = WEIGHT_SCALE, updatable = false)
    private BigDecimal weight;

    StrategyWeightSQL(StrategySQL strategy, String weightSet, String metric, BigDecimal weight) {
        this.strategy = strategy;
        this.weightSet = weightSet;
        this.metric = metric;
        this.weight = weight;
    }
}
