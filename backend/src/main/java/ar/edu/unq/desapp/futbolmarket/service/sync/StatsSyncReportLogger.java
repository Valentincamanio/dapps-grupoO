package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.RunCut;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedStatsMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedTeam;

/**
 * Escribe en el registro el informe de una corrida de WhoScored (FR-048 y FR-049; research D16):
 * una línea por liga, en WARN la cortada; las líneas de detalle que tengan algo para listar; y una
 * línea final con la duración, los totales y el corte si lo hubo. La línea de inicio la escribe
 * {@link StatsSyncService}.
 *
 * <p>Solo traduce el informe a texto: qué ligas se cortaron y qué se omitió lo decide el modelo.</p>
 */
@Component
public class StatsSyncReportLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsSyncReportLogger.class);
    private static final String SEPARATOR = ", ";

    public void log(StatsSyncReport report) {
        report.leagues().forEach(StatsSyncReportLogger::logLeague);
        String summary = summary(report);
        LOGGER.info(summary);
    }

    private static void logLeague(LeagueStatsResult result) {
        String league = result.league().name();
        String line = leagueLine(league, result);
        if (result.status() == LeagueStatsStatus.CUT) {
            LOGGER.warn(line);
        } else {
            LOGGER.info(line);
        }
        logDetail(league + ": partidos omitidos",
                result.skippedMatches().stream().map(StatsSyncReportLogger::describeSkippedMatch).toList());
        logDetail(league + ": equipos sin cruzar",
                result.uncrossedTeams().stream().map(StatsSyncReportLogger::describeUncrossedTeam).toList());
        logDetail(league + ": jugadores sin cruzar",
                result.uncrossedPlayers().stream().map(StatsSyncReportLogger::describeUncrossedPlayer).toList());
        logDetail(league + ": entradas del snapshot salteadas",
                result.skippedSnapshotEntries().entrySet().stream()
                        .map(StatsSyncReportLogger::describeSkippedEntries)
                        .toList());
    }

    /**
     * Una línea de detalle se escribe solo si hay algo para listar.
     */
    private static void logDetail(String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        String joined = String.join(SEPARATOR, items);
        LOGGER.info("{}: {}", title, joined);
    }

    private static String leagueLine(String league, LeagueStatsResult result) {
        return ("%s: %s. Partidos: %d desde el snapshot y %d por scraping; %d omitidos; %d equipos sin cruzar; "
                + "%d jugadores sin cruzar; %d entradas del snapshot salteadas.").formatted(
                league, describeStatus(result.status()), result.matchesFromSnapshot(), result.matchesScraped(),
                result.skippedMatches().size(), result.uncrossedTeams().size(), result.uncrossedPlayers().size(),
                skippedEntries(result));
    }

    private static String summary(StatsSyncReport report) {
        int fromSnapshot = report.leagues().stream().mapToInt(LeagueStatsResult::matchesFromSnapshot).sum();
        int scraped = report.leagues().stream().mapToInt(LeagueStatsResult::matchesScraped).sum();
        return ("Sincronización de WhoScored %s (%s) terminada en %d s: %d partidos procesados (%d desde el snapshot "
                + "y %d por scraping), %d omitidos, %s").formatted(report.type(), report.origin(),
                report.duration().toSeconds(), report.processedMatches(), fromSnapshot, scraped,
                report.skippedMatches(), describeCut(report.cut()));
    }

    private static String describeStatus(LeagueStatsStatus status) {
        return switch (status) {
            case PROCESSED -> "procesada";
            case CUT -> "cortada por bloqueo";
            case NOT_REACHED -> "no alcanzada por el corte";
        };
    }

    /**
     * Sin {@code , partido} si la página no era la de un partido.
     */
    private static String describeCut(RunCut cut) {
        if (cut == null) {
            return "sin corte.";
        }
        String match = cut.matchExternalId() == null ? "" : ", partido " + cut.matchExternalId();
        return "cortada por bloqueo en la página %s (%s%s).".formatted(cut.page().path(), cut.league(), match);
    }

    private static int skippedEntries(LeagueStatsResult result) {
        return result.skippedSnapshotEntries().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String describeSkippedMatch(SkippedStatsMatch skipped) {
        return "%s (%s - %s, %s)".formatted(skipped.matchExternalId(), skipped.homeTeamName(),
                skipped.awayTeamName(), skipped.reason());
    }

    private static String describeUncrossedTeam(UncrossedTeam team) {
        return "%s (%s, %s)".formatted(team.name(), team.teamExternalId(), team.reason());
    }

    private static String describeUncrossedPlayer(UncrossedPlayer player) {
        return "%s (%s, partido %s, %s)".formatted(player.name(), player.teamName(), player.matchExternalId(),
                player.reason());
    }

    private static String describeSkippedEntries(Map.Entry<SnapshotSkipReason, Integer> skipped) {
        return "%s %d".formatted(skipped.getKey(), skipped.getValue());
    }
}
