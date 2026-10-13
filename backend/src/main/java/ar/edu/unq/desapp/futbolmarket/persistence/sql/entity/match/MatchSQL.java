package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.season.SeasonSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
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
 * El resultado se guarda en cuatro columnas sueltas y no en un {@code @Embeddable}: son menos
 * anotaciones para el mismo dato (research D16). {@code status} y {@code winner} son texto, como
 * {@code SeasonSQL.league}. El constructor lo genera Lombok porque son trece columnas.
 */
@Entity
@Table(name = "matches", indexes = @Index(name = "ux_matches_external_id", columnList = "external_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MatchSQL {
    private static final int EXTERNAL_ID_LENGTH = 32;
    private static final int STATUS_LENGTH = 20;
    private static final int WINNER_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = EXTERNAL_ID_LENGTH)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private SeasonSQL season;

    @Column(name = "utc_date", nullable = false)
    private Instant utcDate;

    @Column
    private Integer matchday;

    @Column(nullable = false, length = STATUS_LENGTH)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private TeamSQL homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private TeamSQL awayTeam;

    @Column(name = "full_time_home")
    private Integer fullTimeHome;

    @Column(name = "full_time_away")
    private Integer fullTimeAway;

    @Column(name = "half_time_home")
    private Integer halfTimeHome;

    @Column(name = "half_time_away")
    private Integer halfTimeAway;

    @Column(length = WINNER_LENGTH)
    private String winner;
}
