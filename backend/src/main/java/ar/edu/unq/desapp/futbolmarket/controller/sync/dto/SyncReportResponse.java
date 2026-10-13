package ar.edu.unq.desapp.futbolmarket.controller.sync.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PlayerSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;

/**
 * El informe de una sincronización, con los campos y los nombres del esquema
 * {@code SyncReportResponse} del contrato (FR-043).
 *
 * <p>Los records anidados llevan los nombres de los esquemas del contrato. Cuatro coinciden con
 * tipos del modelo ({@code EntityCounts}, {@code SkippedPlayer}, {@code SkippedMatch} y
 * {@code DuplicatedPlayer}): como el record anidado tapa al del modelo dentro de este archivo, en
 * sus {@code from} el tipo del modelo va con su paquete.</p>
 */
public record SyncReportResponse(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt,
                                 boolean inactivationApplied, List<LeagueSyncReport> leagues,
                                 List<DuplicatedPlayer> duplicatedPlayers, List<PlayerSummary> inactivatedPlayers) {

    public static SyncReportResponse from(SyncReport report) {
        return new SyncReportResponse(
                report.type(),
                report.origin(),
                report.startedAt(),
                report.finishedAt(),
                report.inactivationApplied(),
                report.leagues().stream().map(LeagueSyncReport::from).toList(),
                report.duplicatedPlayers().stream().map(DuplicatedPlayer::from).toList(),
                report.inactivatedPlayers().stream().map(PlayerSummary::from).toList());
    }

    /**
     * Una liga del informe. Una liga fallida no trae temporada.
     */
    public record LeagueSyncReport(League league, LeagueSyncStatus status, String failureReason, SeasonSummary season,
                                   EntityCounts teams, EntityCounts players, EntityCounts matches,
                                   List<SkippedPlayer> skippedPlayers, List<SkippedMatch> skippedMatches,
                                   List<PlayerSummary> reactivatedPlayers) {

        static LeagueSyncReport from(LeagueSyncResult result) {
            return new LeagueSyncReport(
                    result.league(),
                    result.status(),
                    result.failureReason(),
                    result.season() == null ? null : SeasonSummary.from(result.season()),
                    EntityCounts.from(result.teams()),
                    EntityCounts.from(result.players()),
                    EntityCounts.from(result.matches()),
                    result.skippedPlayers().stream().map(SkippedPlayer::from).toList(),
                    result.skippedMatches().stream().map(SkippedMatch::from).toList(),
                    result.reactivatedPlayers().stream().map(PlayerSummary::from).toList());
        }
    }

    public record SeasonSummary(String externalId, LocalDate startDate, LocalDate endDate, Integer currentMatchday) {

        static SeasonSummary from(Season season) {
            return new SeasonSummary(season.externalId(), season.startDate(), season.endDate(),
                    season.currentMatchday());
        }
    }

    public record EntityCounts(int created, int updated, int skipped) {

        static EntityCounts from(ar.edu.unq.desapp.futbolmarket.modelo.sync.EntityCounts counts) {
            return new EntityCounts(counts.created(), counts.updated(), counts.skipped());
        }
    }

    /**
     * Un jugador guardado: para un inactivado, {@code team} es su último equipo.
     */
    public record PlayerSummary(Long id, String externalId, String name, String team) {

        static PlayerSummary from(Player player) {
            return new PlayerSummary(player.id(), player.externalId(), player.name(), player.team().name());
        }
    }

    public record SkippedPlayer(String externalId, String name, String team, PlayerSkipReason reason) {

        static SkippedPlayer from(ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedPlayer skipped) {
            return new SkippedPlayer(skipped.externalId(), skipped.name(), skipped.teamName(), skipped.reason());
        }
    }

    public record SkippedMatch(String externalId, String homeTeamExternalId, String awayTeamExternalId,
                               Instant utcDate, MatchSkipReason reason) {

        static SkippedMatch from(ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedMatch skipped) {
            return new SkippedMatch(skipped.externalId(), skipped.homeTeamExternalId(), skipped.awayTeamExternalId(),
                    skipped.utcDate(), skipped.reason());
        }
    }

    public record DuplicatedPlayer(String externalId, String name, String keptTeam, String ignoredTeam) {

        static DuplicatedPlayer from(ar.edu.unq.desapp.futbolmarket.modelo.sync.DuplicatedPlayer duplicated) {
            return new DuplicatedPlayer(duplicated.externalId(), duplicated.name(), duplicated.keptTeamName(),
                    duplicated.ignoredTeamName());
        }
    }
}
