package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Una fila por versión de una estrategia (research D13). La versión 1 es la raíz y tiene
 * {@code root_id} en {@code NULL}; las siguientes apuntan a ella, así el id de la estrategia es el
 * de su versión 1.
 *
 * <p>Los parámetros de una versión no cambian nunca: lo único modificable es la marca
 * {@code active}, que tiene exactamente una fila de toda la tabla en {@code true} (lo garantiza
 * {@code StrategyCatalog} dentro de una transacción). {@code created_at} lo asigna Hibernate al
 * insertar.</p>
 */
@Entity
@Table(name = "strategies",
        indexes = @Index(name = "ux_strategies_name_version", columnList = "name, version_number", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategySQL {
    private static final int NAME_LENGTH = 100;
    private static final int SCALE_FACTOR_PRECISION = 8;
    private static final int VALUE_PRECISION = 12;
    private static final int VALUE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_id", updatable = false)
    private StrategySQL root;

    @Column(nullable = false, length = NAME_LENGTH, updatable = false)
    private String name;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "scale_factor", nullable = false, updatable = false,
            precision = SCALE_FACTOR_PRECISION, scale = VALUE_SCALE)
    private BigDecimal scaleFactor;

    @Column(name = "minimum_value", nullable = false, updatable = false,
            precision = VALUE_PRECISION, scale = VALUE_SCALE)
    private BigDecimal minimumValue;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "strategy", cascade = CascadeType.ALL)
    private List<StrategyWeightSQL> weights = new ArrayList<>();

    public StrategySQL(StrategySQL root, String name, int versionNumber, BigDecimal scaleFactor,
                       BigDecimal minimumValue, boolean active) {
        this.root = root;
        this.name = name;
        this.versionNumber = versionNumber;
        this.scaleFactor = scaleFactor;
        this.minimumValue = minimumValue;
        this.active = active;
    }

    /**
     * El id de la estrategia a la que pertenece la versión: el de la raíz, o el propio si es la
     * versión 1.
     */
    public Long strategyId() {
        return root == null ? id : root.getId();
    }

    public void addWeight(String weightSet, String metric, BigDecimal weight) {
        weights.add(new StrategyWeightSQL(this, weightSet, metric, weight));
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}
