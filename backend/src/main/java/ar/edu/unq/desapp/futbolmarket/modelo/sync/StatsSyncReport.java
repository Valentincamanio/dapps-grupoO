package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El informe de una corrida de WhoScored (FR-048). Va al registro y a la respuesta del disparo
 * manual; no se guarda. Las ligas van en el orden del enum, una por liga pedida, y {@code cut} es
 * {@code null} si no hubo corte.
 */
public record StatsSyncReport(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt,
                              SnapshotStatus snapshot, RunCut cut, List<LeagueStatsResult> leagues) {
    public StatsSyncReport {
        leagues = List.copyOf(leagues);
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    /**
     * Los partidos procesados, desde el snapshot y por scraping, en todas las ligas.
     */
    public int processedMatches() {
        return leagues.stream().mapToInt(league -> league.matchesFromSnapshot() + league.matchesScraped()).sum();
    }

    public int skippedMatches() {
        return leagues.stream().mapToInt(league -> league.skippedMatches().size()).sum();
    }

    /**
     * Solo una corrida completa y sin corte es la última completa exitosa de WhoScored (FR-047). Una
     * de una sola liga nunca cuenta.
     */
    public Optional<LastFullSync> lastFullSync() {
        if (type != SyncType.FULL || cut != null) {
            return Optional.empty();
        }
        return Optional.of(new LastFullSync(SyncSource.WHOSCORED, finishedAt));
    }
}
