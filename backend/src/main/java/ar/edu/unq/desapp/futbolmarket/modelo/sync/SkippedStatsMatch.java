package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;

/**
 * Un partido pendiente que no se procesó, con lo necesario para reconocerlo en el informe.
 */
public record SkippedStatsMatch(String matchExternalId, String homeTeamName, String awayTeamName, Instant utcDate,
                                StatsSkipReason reason) {
}
