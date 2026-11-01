package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player.PlayerSQL;
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
 * Un jugador potenciado en una semana, identificada por su lunes. Las filas no se actualizan:
 * cambiar los potenciados de una semana borra las suyas e inserta las nuevas.
 */
@Entity
@Table(name = "boosts", indexes = {
        @Index(name = "ux_boosts_week_player", columnList = "week_start, player_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoostSQL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerSQL player;

    @Column(name = "boost_percent", nullable = false, updatable = false)
    private int percent;

    @Column(name = "position_in_week", nullable = false, updatable = false)
    private int positionInWeek;

    public BoostSQL(LocalDate weekStart, PlayerSQL player, int percent, int positionInWeek) {
        this.weekStart = weekStart;
        this.player = player;
        this.percent = percent;
        this.positionInWeek = positionInWeek;
    }
}
