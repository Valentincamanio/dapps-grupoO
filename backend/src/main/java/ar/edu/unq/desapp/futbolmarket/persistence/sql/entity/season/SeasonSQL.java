package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.season;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seasons", indexes = @Index(name = "ux_seasons_external_id", columnList = "external_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonSQL {
    private static final int EXTERNAL_ID_LENGTH = 32;
    private static final int LEAGUE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = EXTERNAL_ID_LENGTH)
    private String externalId;

    /**
     * Es texto y no el enum del modelo; la traducción la hace {@code SeasonMapper}. Con
     * {@code @Enumerated}, Hibernate crea en H2 una columna {@code ENUM} nativa que
     * {@code ddl-auto: update} no hace evolucionar (research D16).
     */
    @Column(nullable = false, length = LEAGUE_LENGTH)
    private String league;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "current_matchday")
    private Integer currentMatchday;

    public SeasonSQL(Long id, String externalId, String league, LocalDate startDate, LocalDate endDate,
                     Integer currentMatchday) {
        this.id = id;
        this.externalId = externalId;
        this.league = league;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentMatchday = currentMatchday;
    }
}
