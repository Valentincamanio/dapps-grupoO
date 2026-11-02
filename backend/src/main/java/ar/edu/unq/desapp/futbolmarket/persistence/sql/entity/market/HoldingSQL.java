package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import java.math.BigDecimal;

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
 * La tenencia de un usuario de un jugador (data-model 6.2), con los dos acumuladores de su costo
 * en vez de un precio promedio redondeado (research D12). El índice único deja una sola fila por
 * titular y jugador; la fila se conserva aunque la tenencia llegue a 0.
 */
@Entity
@Table(name = "holdings", indexes = {
        @Index(name = "ux_holdings_owner_player", columnList = "owner_id, player_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class HoldingSQL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private AppUserSQL owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerSQL player;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "committed_quantity", nullable = false)
    private int committedQuantity;

    @Column(name = "acquired_quantity", nullable = false)
    private int acquiredQuantity;

    @Column(name = "acquired_cost", nullable = false, precision = MarketColumns.CREDIT_PRECISION,
            scale = MarketColumns.CREDIT_SCALE)
    private BigDecimal acquiredCost;
}
