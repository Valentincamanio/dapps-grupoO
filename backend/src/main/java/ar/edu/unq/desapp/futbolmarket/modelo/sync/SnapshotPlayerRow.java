package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;

/**
 * Una fila de jugador de una entrada {@code stats}: el jugador por su id de Football-Data y sus
 * estadísticas en el partido. {@code whoscoredId} es {@code null} si el jugador no está cruzado:
 * sus estadísticas se cargaron con un {@code CONFLICTING_CROSS} y el id quedó en otro jugador.
 */
public record SnapshotPlayerRow(String playerExternalId, String whoscoredId, boolean starter, int minutesPlayed,
                                MetricCounts counts, BigDecimal rating, boolean manOfTheMatch) {
    public SnapshotPlayerRow {
        counts = counts == null ? MetricCounts.empty() : counts;
    }
}
