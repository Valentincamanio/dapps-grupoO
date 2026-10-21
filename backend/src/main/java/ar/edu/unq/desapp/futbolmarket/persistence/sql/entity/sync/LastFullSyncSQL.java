package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.sync;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una fila por fuente con el momento en que terminó su última sincronización completa exitosa
 * (FR-047). La fuente es texto y no el enum del modelo; la traducción la hace
 * {@code LastFullSyncMapper}, como en {@code SeasonSQL.league}.
 */
@Entity
@Table(name = "last_full_syncs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LastFullSyncSQL {
    private static final int SOURCE_LENGTH = 20;

    @Id
    @Column(length = SOURCE_LENGTH)
    private String source;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;
}
