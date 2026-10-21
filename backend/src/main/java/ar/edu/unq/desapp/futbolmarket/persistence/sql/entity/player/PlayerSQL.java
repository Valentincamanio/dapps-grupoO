package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.player;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team.TeamSQL;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * El id de WhoScored es único, pero nulo hasta que el jugador se cruza (FR-020 y FR-022): el
 * índice admite varios {@code NULL}.
 */
@Entity
@Table(name = "players", indexes = @Index(name = "ux_players_whoscored_id", columnList = "whoscored_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSQL {
    private static final int NATIONALITY_LENGTH = 100;
    private static final int WHOSCORED_ID_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamSQL team;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = NATIONALITY_LENGTH)
    private String nationality;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "whoscored_id", length = WHOSCORED_ID_LENGTH)
    private String whoscoredId;

    public PlayerSQL(Long id, String externalId, String name, Position position, TeamSQL team,
                     LocalDate dateOfBirth, String nationality, boolean active, String whoscoredId) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.position = position;
        this.team = team;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.active = active;
        this.whoscoredId = whoscoredId;
    }
}
