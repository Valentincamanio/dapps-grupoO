package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un equipo se reconoce por el id que le da la fuente y no por su nombre, que puede cambiar
 * (FR-006). Por eso {@code external_id} tiene un índice único con nombre fijo.
 */
@Entity
@Table(name = "teams", indexes = @Index(name = "ux_teams_external_id", columnList = "external_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamSQL {
    private static final int EXTERNAL_ID_LENGTH = 32;
    private static final int CREST_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = EXTERNAL_ID_LENGTH)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(length = CREST_LENGTH)
    private String crest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private League league;

    public TeamSQL(Long id, String externalId, String name, String crest, League league) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.crest = crest;
        this.league = league;
    }
}
