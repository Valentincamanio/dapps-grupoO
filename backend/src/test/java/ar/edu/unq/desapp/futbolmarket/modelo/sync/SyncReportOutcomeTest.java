package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.season;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.seasonId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * Si una corrida terminó bien, según cada informe (research D6 de la 012), y que la última completa
 * exitosa sigue siendo la misma después de reutilizar {@code succeeded()}.
 */
class SyncReportOutcomeTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-16T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(42);
    private static final String DOWNLOAD_FAILURE = "La fuente no respondió a tiempo.";
    private static final RunCut CUT = new RunCut(new BlockedPage(PageKind.LEAGUE, "252", "/regions/252"),
            League.PREMIER, null);

    @Test
    void unInformeDeFootballDataSinLigasFallidasTerminoBien() {
        SyncReport report = syncReport(SyncType.FULL, List.of(succeeded(League.PREMIER)));

        boolean succeeded = report.succeeded();

        assertThat(succeeded).isTrue();
    }

    @Test
    void unInformeDeFootballDataConUnaLigaFallidaNoTerminoBien() {
        SyncReport report = syncReport(SyncType.FULL,
                List.of(succeeded(League.PREMIER), LeagueSyncResult.failed(League.LIGUE_1, DOWNLOAD_FAILURE)));

        boolean succeeded = report.succeeded();

        assertThat(succeeded).isFalse();
    }

    @Test
    void unInformeDeWhoScoredSinCorteTerminoBien() {
        StatsSyncReport report = statsReport(SyncType.FULL, null);

        boolean succeeded = report.succeeded();

        assertThat(succeeded).isTrue();
    }

    @Test
    void unInformeDeWhoScoredConCorteNoTerminoBien() {
        StatsSyncReport report = statsReport(SyncType.FULL, CUT);

        boolean succeeded = report.succeeded();

        assertThat(succeeded).isFalse();
    }

    @Test
    void laUltimaCompletaDeFootballDataSigueIgual() {
        SyncReport fullOk = syncReport(SyncType.FULL, List.of(succeeded(League.PREMIER)));
        SyncReport fullFailed = syncReport(SyncType.FULL,
                List.of(LeagueSyncResult.failed(League.PREMIER, DOWNLOAD_FAILURE)));
        SyncReport single = syncReport(SyncType.SINGLE_LEAGUE, List.of(succeeded(League.PREMIER)));

        var lastOfFullOk = fullOk.lastFullSync();
        var lastOfFullFailed = fullFailed.lastFullSync();
        var lastOfSingle = single.lastFullSync();

        assertThat(lastOfFullOk).contains(new LastFullSync(SyncSource.FOOTBALL_DATA, FINISHED_AT));
        assertThat(lastOfFullFailed).isEmpty();
        assertThat(lastOfSingle).isEmpty();
    }

    @Test
    void laUltimaCompletaDeWhoScoredSigueIgual() {
        StatsSyncReport fullOk = statsReport(SyncType.FULL, null);
        StatsSyncReport fullCut = statsReport(SyncType.FULL, CUT);
        StatsSyncReport single = statsReport(SyncType.SINGLE_LEAGUE, null);

        var lastOfFullOk = fullOk.lastFullSync();
        var lastOfFullCut = fullCut.lastFullSync();
        var lastOfSingle = single.lastFullSync();

        assertThat(lastOfFullOk).contains(new LastFullSync(SyncSource.WHOSCORED, FINISHED_AT));
        assertThat(lastOfFullCut).isEmpty();
        assertThat(lastOfSingle).isEmpty();
    }

    private static SyncReport syncReport(SyncType type, List<LeagueSyncResult> leagues) {
        return new SyncReport(type, SyncOrigin.MANUAL, STARTED_AT, FINISHED_AT, leagues, List.of(), List.of(), false);
    }

    private static StatsSyncReport statsReport(SyncType type, RunCut cut) {
        return new StatsSyncReport(type, SyncOrigin.MANUAL, STARTED_AT, FINISHED_AT, SnapshotStatus.LOADED, cut,
                List.of());
    }

    private static LeagueSyncResult succeeded(League league) {
        return new LeagueSyncResult(league, LeagueSyncStatus.SUCCEEDED, null, season(league, seasonId(league)),
                EntityCounts.none(), EntityCounts.none(), EntityCounts.none(), List.of(), List.of(), List.of());
    }
}
