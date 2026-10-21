package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match.MatchSQL;
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
import lombok.Setter;

/**
 * Una fila por jugador y partido: el índice único sobre los dos impide guardar dos veces el
 * rendimiento de un jugador en el mismo partido.
 *
 * <p>Las 15 métricas son una columna cada una y no van en el constructor: el mapper las lee y las
 * escribe en un único recorrido de {@code Metric}, con los getters y los setters, así no se
 * repite un bloque por métrica.</p>
 */
@Entity
@Table(name = "player_match_stats",
        indexes = @Index(name = "ux_player_match_stats_player_match", columnList = "player_id, match_id", unique = true))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerMatchStatsSQL {
    private static final int RATING_PRECISION = 4;
    private static final int RATING_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerSQL player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchSQL match;

    @Column(nullable = false)
    private boolean starter;

    @Column(name = "minutes_played", nullable = false)
    private int minutesPlayed;

    @Column(nullable = false)
    private int goals;

    @Column(nullable = false)
    private int assists;

    @Column(nullable = false)
    private int shots;

    @Column(name = "shots_on_target", nullable = false)
    private int shotsOnTarget;

    @Column(name = "key_passes", nullable = false)
    private int keyPasses;

    @Column(nullable = false)
    private int passes;

    @Column(name = "accurate_passes", nullable = false)
    private int accuratePasses;

    @Column(name = "successful_dribbles", nullable = false)
    private int successfulDribbles;

    @Column(nullable = false)
    private int tackles;

    @Column(nullable = false)
    private int interceptions;

    @Column(nullable = false)
    private int clearances;

    @Column(name = "aerials_won", nullable = false)
    private int aerialsWon;

    @Column(name = "fouls_committed", nullable = false)
    private int foulsCommitted;

    @Column(name = "yellow_cards", nullable = false)
    private int yellowCards;

    @Column(name = "red_cards", nullable = false)
    private int redCards;

    @Column(precision = RATING_PRECISION, scale = RATING_SCALE)
    private BigDecimal rating;

    @Column(name = "man_of_the_match", nullable = false)
    private boolean manOfTheMatch;

    public PlayerMatchStatsSQL(Long id, PlayerSQL player, MatchSQL match, boolean starter, int minutesPlayed,
                               BigDecimal rating, boolean manOfTheMatch) {
        this.id = id;
        this.player = player;
        this.match = match;
        this.starter = starter;
        this.minutesPlayed = minutesPlayed;
        this.rating = rating;
        this.manOfTheMatch = manOfTheMatch;
    }
}
