package ar.edu.unq.desapp.futbolmarket.controller.sync.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CrossFailure;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PageKind;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.RunCut;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;

/**
 * El informe de una sincronización de WhoScored, con los campos y los nombres del esquema
 * {@code StatsSyncReportResponse} del contrato (FR-048).
 *
 * <p>Los records anidados llevan los nombres de los esquemas del contrato. Tres coinciden con
 * tipos del modelo ({@code SkippedStatsMatch}, {@code UncrossedTeam} y {@code UncrossedPlayer}):
 * como el record anidado tapa al del modelo dentro de este archivo, en sus {@code from} el tipo del
 * modelo va con su paquete. {@code cut} es {@code null} si la corrida no se cortó.</p>
 */
public record StatsSyncReportResponse(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt,
                                      SnapshotStatus snapshot, RunCutReport cut, List<LeagueStatsReport> leagues) {

    public static StatsSyncReportResponse from(StatsSyncReport report) {
        return new StatsSyncReportResponse(
                report.type(),
                report.origin(),
                report.startedAt(),
                report.finishedAt(),
                report.snapshot(),
                report.cut() == null ? null : RunCutReport.from(report.cut()),
                report.leagues().stream().map(LeagueStatsReport::from).toList());
    }

    /**
     * Dónde se cortó la corrida por bloqueo. {@code match} es el id de Football-Data del partido,
     * o {@code null} si la página no era la de un partido.
     */
    public record RunCutReport(PageKind page, String whoscoredId, String path, League league, String match) {

        static RunCutReport from(RunCut cut) {
            return new RunCutReport(cut.page().kind(), cut.page().whoscoredId(), cut.page().path(), cut.league(),
                    cut.matchExternalId());
        }
    }

    /**
     * Una liga del informe. Las entradas del snapshot salteadas van en el orden del enum.
     */
    public record LeagueStatsReport(League league, LeagueStatsStatus status, int matchesFromSnapshot,
                                    int matchesScraped, List<SkippedStatsMatch> skippedMatches,
                                    List<UncrossedTeam> uncrossedTeams, List<UncrossedPlayer> uncrossedPlayers,
                                    List<SnapshotSkipCount> skippedSnapshotEntries) {

        static LeagueStatsReport from(LeagueStatsResult result) {
            return new LeagueStatsReport(
                    result.league(),
                    result.status(),
                    result.matchesFromSnapshot(),
                    result.matchesScraped(),
                    result.skippedMatches().stream().map(SkippedStatsMatch::from).toList(),
                    result.uncrossedTeams().stream().map(UncrossedTeam::from).toList(),
                    result.uncrossedPlayers().stream().map(UncrossedPlayer::from).toList(),
                    SnapshotSkipCount.from(result.skippedSnapshotEntries()));
        }
    }

    public record SkippedStatsMatch(String externalId, String homeTeam, String awayTeam, Instant utcDate,
                                    StatsSkipReason reason) {

        static SkippedStatsMatch from(ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedStatsMatch skipped) {
            return new SkippedStatsMatch(skipped.matchExternalId(), skipped.homeTeamName(), skipped.awayTeamName(),
                    skipped.utcDate(), skipped.reason());
        }
    }

    public record UncrossedTeam(String externalId, String name, CrossFailure reason) {

        static UncrossedTeam from(ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedTeam uncrossed) {
            return new UncrossedTeam(uncrossed.teamExternalId(), uncrossed.name(), uncrossed.reason());
        }
    }

    /**
     * Un jugador de WhoScored sin cruzar: {@code team} es el nombre del equipo del catálogo y
     * {@code match}, el id de Football-Data del partido.
     */
    public record UncrossedPlayer(String whoscoredId, String name, String team, String match, CrossFailure reason) {

        static UncrossedPlayer from(ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedPlayer uncrossed) {
            return new UncrossedPlayer(uncrossed.whoscoredId(), uncrossed.name(), uncrossed.teamName(),
                    uncrossed.matchExternalId(), uncrossed.reason());
        }
    }

    public record SnapshotSkipCount(SnapshotSkipReason reason, int count) {

        /**
         * Una entrada por motivo con al menos una salteada, en el orden del enum. Vacía si no hubo
         * ninguna.
         */
        static List<SnapshotSkipCount> from(Map<SnapshotSkipReason, Integer> skipped) {
            return Arrays.stream(SnapshotSkipReason.values())
                    .filter(skipped::containsKey)
                    .map(reason -> new SnapshotSkipCount(reason, skipped.get(reason)))
                    .toList();
        }
    }
}
