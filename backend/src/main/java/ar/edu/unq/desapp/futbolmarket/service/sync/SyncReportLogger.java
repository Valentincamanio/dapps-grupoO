package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.DuplicatedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.EntityCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.InactivationSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;

/**
 * Escribe en el registro el informe de una sincronización (FR-044 y research D19): una línea por
 * liga, las líneas de detalle que tengan algo para listar y una línea final. La línea de inicio no
 * es de acá: la escribe {@link SyncService} al tomar el semáforo.
 *
 * <p>Solo traduce el informe a texto. Qué ligas fallaron y por qué no se inactivó a nadie lo decide
 * el modelo (Principio II).</p>
 */
@Component
public class SyncReportLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncReportLogger.class);
    private static final String SEPARATOR = ", ";

    public void log(SyncReport report) {
        report.leagues().forEach(SyncReportLogger::logLeague);
        logDetail("Jugadores en más de un plantel",
                report.duplicatedPlayers().stream().map(SyncReportLogger::describeDuplicate).toList());
        logDetail("Jugadores inactivados",
                report.inactivatedPlayers().stream().map(SyncReportLogger::describePlayer).toList());
        String summary = summary(report);
        LOGGER.info(summary);
    }

    private static void logLeague(LeagueSyncResult result) {
        String league = result.league().name();
        if (!result.succeeded()) {
            String reason = result.failureReason();
            LOGGER.warn("{}: fallida. Motivo: {}", league, reason);
            return;
        }
        String processed = processedLine(league, result);
        LOGGER.info(processed);
        logDetail(league + ": jugadores omitidos",
                result.skippedPlayers().stream().map(SyncReportLogger::describeSkippedPlayer).toList());
        logDetail(league + ": partidos omitidos",
                result.skippedMatches().stream().map(SyncReportLogger::describeSkippedMatch).toList());
        logDetail(league + ": jugadores reactivados",
                result.reactivatedPlayers().stream().map(SyncReportLogger::describePlayer).toList());
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

    private static String processedLine(String league, LeagueSyncResult result) {
        return "%s: procesada. Equipos %s, jugadores %s, partidos %s (creados/actualizados/omitidos).%s".formatted(
                league, counts(result.teams()), counts(result.players()), counts(result.matches()),
                describeSeason(result.season()));
    }

    private static String summary(SyncReport report) {
        int failed = report.failedLeagues().size();
        int processed = report.leagues().size() - failed;
        String skipReason = report.inactivationSkipReason()
                .map(reason -> " " + describeSkipReason(reason))
                .orElse("");
        return "Sincronización %s (%s) terminada en %d s: %d ligas procesadas, %d fallidas, %d jugadores inactivados.%s"
                .formatted(report.type(), report.origin(), report.duration().toSeconds(), processed, failed,
                        report.inactivatedPlayers().size(), skipReason);
    }

    private static String describeSkipReason(InactivationSkipReason reason) {
        return switch (reason) {
            case SINGLE_LEAGUE -> "No se inactivó a nadie: fue una sincronización de una sola liga.";
            case FAILED_LEAGUES -> "No se inactivó a nadie: hubo ligas fallidas.";
        };
    }

    private static String counts(EntityCounts counts) {
        return counts.created() + "/" + counts.updated() + "/" + counts.skipped();
    }

    private static String describeSeason(Season season) {
        if (season == null) {
            return "";
        }
        String matchday = season.currentMatchday() == null ? "" : ", jornada " + season.currentMatchday();
        return " Temporada %s: %s a %s%s.".formatted(season.externalId(), season.startDate(), season.endDate(), matchday);
    }

    private static String describeSkippedPlayer(SkippedPlayer skipped) {
        return "%s (%s, %s)".formatted(nameOrId(skipped.name(), skipped.externalId()), skipped.teamName(),
                skipped.reason());
    }

    private static String describeSkippedMatch(SkippedMatch skipped) {
        return "%s (%s vs %s, %s)".formatted(skipped.externalId(), skipped.homeTeamExternalId(),
                skipped.awayTeamExternalId(), skipped.reason());
    }

    private static String describeDuplicate(DuplicatedPlayer duplicated) {
        return "%s (queda en %s; se ignora %s)".formatted(nameOrId(duplicated.name(), duplicated.externalId()),
                duplicated.keptTeamName(), duplicated.ignoredTeamName());
    }

    private static String describePlayer(Player player) {
        return "%s (%s)".formatted(player.name(), player.team().name());
    }

    /**
     * Un jugador omitido puede no tener nombre: se lo identifica por el id de la fuente.
     */
    private static String nameOrId(String name, String externalId) {
        return name != null ? name : "jugador " + externalId;
    }
}
