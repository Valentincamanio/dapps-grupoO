package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * La corrida de WhoScored en curso. Es mutable porque acumula los resultados mientras corre, y la
 * usa un solo hilo, igual que {@link SyncRun}.
 *
 * <p>Si WhoScored bloquea una página, la corrida se corta: las ligas pedidas que no llegaron a tener
 * resultado quedan {@code NOT_REACHED} (FR-044).</p>
 */
public class StatsSyncRun {
    private final SyncType type;
    private final SyncOrigin origin;
    private final Instant startedAt;
    private final List<League> leagues;
    private final Map<League, LeagueStatsResult> results = new EnumMap<>(League.class);
    private SnapshotStatus snapshotStatus;
    private RunCut cut;

    private StatsSyncRun(SyncType type, SyncOrigin origin, Instant startedAt, List<League> leagues) {
        this.type = type;
        this.origin = origin;
        this.startedAt = startedAt;
        this.leagues = leagues.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public static StatsSyncRun full(SyncOrigin origin, Instant startedAt) {
        return new StatsSyncRun(SyncType.FULL, origin, startedAt, List.of(League.values()));
    }

    /**
     * Solo el disparo manual puede pedir una sola liga.
     */
    public static StatsSyncRun singleLeague(League league, Instant startedAt) {
        return new StatsSyncRun(SyncType.SINGLE_LEAGUE, SyncOrigin.MANUAL, startedAt, List.of(league));
    }

    /**
     * Las ligas pedidas, en el orden del enum.
     */
    public List<League> leagues() {
        return leagues;
    }

    public void snapshotRead(SnapshotRead read) {
        snapshotStatus = read.status();
    }

    public void record(LeagueStatsResult result) {
        results.put(result.league(), result);
    }

    public void cut(BlockedPage page, League league, String matchExternalId) {
        cut = new RunCut(page, league, matchExternalId);
    }

    public boolean isCut() {
        return cut != null;
    }

    /**
     * Arma el informe con una liga por liga pedida, en el orden del enum.
     */
    public StatsSyncReport finish(Instant finishedAt) {
        List<LeagueStatsResult> leagueResults = leagues.stream()
                .map(league -> results.getOrDefault(league, LeagueStatsResult.notReached(league, 0, Map.of())))
                .toList();
        return new StatsSyncReport(type, origin, startedAt, finishedAt, snapshotStatus, cut, leagueResults);
    }
}
