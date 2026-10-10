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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSQL {
    private static final int NATIONALITY_LENGTH = 100;

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

    public PlayerSQL(Long id, String externalId, String name, Position position, TeamSQL team,
                     LocalDate dateOfBirth, String nationality, boolean active) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.position = position;
        this.team = team;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.active = active;
    }
}
