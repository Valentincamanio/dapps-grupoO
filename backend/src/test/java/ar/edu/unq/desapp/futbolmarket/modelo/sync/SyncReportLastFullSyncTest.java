package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.allSnapshots;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.season;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.seasonId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * La última completa exitosa de Football-Data que deja un informe (FR-047; HU3, escenario 13). Los
 * informes se arman con {@link SyncRun}, como en {@code SyncRunTest}.
 */
class SyncReportLastFullSyncTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(42);
    private static final String DOWNLOAD_FAILURE = "La fuente no respondió a tiempo.";

    @Test
    void unaCompletaConLasCincoLigasEnExitoEsLaUltimaCompletaDeFootballData() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshots());
        run.leagues().forEach(league -> run.recordSuccess(succeeded(league)));

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.lastFullSync()).contains(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
    }

    @Test
    void unaCompletaManualConLasCincoLigasEnExitoTambienCuenta() {
        SyncRun run = SyncRun.full(SyncOrigin.MANUAL, STARTED_AT);
        run.registerSnapshots(allSnapshots());
        run.leagues().forEach(league -> run.recordSuccess(succeeded(league)));

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.lastFullSync()).contains(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
    }

    @Test
    void unaCompletaConUnaLigaFallidaNoCuenta() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshots().stream().filter(league -> league.league() != League.LIGUE_1).toList());
        run.leagues().stream()
                .filter(league -> league != League.LIGUE_1)
                .forEach(league -> run.recordSuccess(succeeded(league)));
        run.recordFailure(League.LIGUE_1, DOWNLOAD_FAILURE);

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.lastFullSync()).isEmpty();
    }

    @Test
    void unaDeUnaSolaLigaNoCuentaAunqueSalgaBien() {
        SyncRun run = SyncRun.singleLeague(League.SERIE_A, STARTED_AT);
        run.registerSnapshots(List.of(snapshot(League.SERIE_A)));
        run.recordSuccess(succeeded(League.SERIE_A));

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.lastFullSync()).isEmpty();
    }

    private static LeagueSyncResult succeeded(League league) {
        return new LeagueSyncResult(league, LeagueSyncStatus.SUCCEEDED, null, season(league, seasonId(league)),
                EntityCounts.none(), EntityCounts.none(), EntityCounts.none(), List.of(), List.of(), List.of());
    }
}
