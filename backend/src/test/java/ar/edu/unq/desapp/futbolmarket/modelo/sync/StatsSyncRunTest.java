package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

class StatsSyncRunTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-10-12T07:47:30Z");
    private static final BlockedPage BLOCKED_MATCH =
            new BlockedPage(PageKind.MATCH, "1983603", "/matches/1983603/live");

    @Test
    void unaCompletaPideLasCincoLigasEnElOrdenDelEnum() {
        StatsSyncRun run = StatsSyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);

        List<League> leagues = run.leagues();

        assertThat(leagues).containsExactly(League.values());
    }

    @Test
    void unaDeUnaLigaEsManualYPideSoloEsaLiga() {
        StatsSyncRun run = StatsSyncRun.singleLeague(League.SERIE_A, STARTED_AT);

        StatsSyncReport report = run.finish(FINISHED_AT);

        assertThat(run.leagues()).containsExactly(League.SERIE_A);
        assertThat(report.type()).isEqualTo(SyncType.SINGLE_LEAGUE);
        assertThat(report.origin()).isEqualTo(SyncOrigin.MANUAL);
    }

    @Test
    void elInformeOrdenaLasLigasYLasQueNoTienenResultadoQuedanNoAlcanzadas() {
        StatsSyncRun run = StatsSyncRun.full(SyncOrigin.MANUAL, STARTED_AT);
        run.record(processed(League.LIGUE_1, 3, 0));
        run.record(processed(League.PREMIER, 5, 2));

        StatsSyncReport report = run.finish(FINISHED_AT);

        assertThat(report.leagues()).extracting(LeagueStatsResult::league).containsExactly(League.values());
        assertThat(report.leagues()).extracting(LeagueStatsResult::status).containsExactly(
                LeagueStatsStatus.PROCESSED, LeagueStatsStatus.NOT_REACHED, LeagueStatsStatus.NOT_REACHED,
                LeagueStatsStatus.NOT_REACHED, LeagueStatsStatus.PROCESSED);
    }

    @Test
    void unaCompletaSinCorteEsLaUltimaCompletaExitosaDeWhoScored() {
        StatsSyncRun run = StatsSyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);

        Optional<LastFullSync> lastFullSync = run.finish(FINISHED_AT).lastFullSync();

        assertThat(lastFullSync).contains(new LastFullSync(SyncSource.WHOSCORED, FINISHED_AT));
    }

    @Test
    void unaDeUnaLigaNuncaEsLaUltimaCompletaExitosa() {
        StatsSyncRun run = StatsSyncRun.singleLeague(League.PREMIER, STARTED_AT);

        Optional<LastFullSync> lastFullSync = run.finish(FINISHED_AT).lastFullSync();

        assertThat(lastFullSync).isEmpty();
    }

    @Test
    void unaCompletaCortadaNoEsLaUltimaCompletaExitosaYElInformeTraeElCorte() {
        StatsSyncRun run = StatsSyncRun.full(SyncOrigin.MANUAL, STARTED_AT);

        run.cut(BLOCKED_MATCH, League.PREMIER, "560603");
        StatsSyncReport report = run.finish(FINISHED_AT);

        assertThat(run.isCut()).isTrue();
        assertThat(report.cut()).isEqualTo(new RunCut(BLOCKED_MATCH, League.PREMIER, "560603"));
        assertThat(report.lastFullSync()).isEmpty();
    }

    @Test
    void elInformeTraeElEstadoDelSnapshotLosTotalesYLaDuracion() {
        StatsSyncRun run = StatsSyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.snapshotRead(SnapshotRead.missing());
        run.record(processed(League.PREMIER, 5, 2));
        run.record(LeagueStatsResult.notReached(League.BUNDESLIGA, 4, Map.of()));

        StatsSyncReport report = run.finish(FINISHED_AT);

        assertThat(report.snapshot()).isEqualTo(SnapshotStatus.MISSING);
        assertThat(report.processedMatches()).isEqualTo(5 + 1 + 4);
        assertThat(report.skippedMatches()).isEqualTo(2);
        assertThat(report.duration()).isEqualTo(Duration.ofMinutes(47).plusSeconds(30));
    }

    /**
     * Un resultado con un partido del snapshot, {@code scraped} scrapeados y {@code skipped}
     * omitidos.
     */
    private static LeagueStatsResult processed(League league, int scraped, int skipped) {
        List<SkippedStatsMatch> skippedMatches = IntStream.range(0, skipped)
                .mapToObj(index -> new SkippedStatsMatch("5606" + index, "Local", "Visitante", STARTED_AT,
                        StatsSkipReason.WRITE_FAILED))
                .toList();
        return new LeagueStatsResult(league, LeagueStatsStatus.PROCESSED, 1, scraped, skippedMatches, List.of(),
                List.of(), Map.of());
    }
}
