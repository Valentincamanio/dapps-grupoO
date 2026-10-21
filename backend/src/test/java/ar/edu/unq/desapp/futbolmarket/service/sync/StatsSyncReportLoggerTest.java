package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredPages;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CrossFailure;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.RunCut;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedStatsMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedTeam;

@ExtendWith(OutputCaptureExtension.class)
class StatsSyncReportLoggerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(2830);

    private final StatsSyncReportLogger logger = new StatsSyncReportLogger();

    @Test
    void unaLigaProcesadaVaEnInfoConSusTotales(CapturedOutput output) {
        StatsSyncReport report = report(null, List.of(processedPremier()));

        logger.log(report);

        assertThat(lineWith(output, "PREMIER: procesada.")).contains("INFO").endsWith(
                "PREMIER: procesada. Partidos: 2 desde el snapshot y 60 por scraping; 1 omitidos; "
                        + "1 equipos sin cruzar; 1 jugadores sin cruzar; 3 entradas del snapshot salteadas.");
    }

    @Test
    void losDetallesListanCadaElementoSeparadoPorComa(CapturedOutput output) {
        StatsSyncReport report = report(null, List.of(processedPremier()));

        logger.log(report);

        assertThat(output).contains(
                "PREMIER: partidos omitidos: 560603 (Everton FC - Manchester United FC, NOT_IN_SOURCE)",
                "PREMIER: equipos sin cruzar: Manchester United FC (66, NOT_FOUND)",
                "PREMIER: jugadores sin cruzar: Mason Miley (Newcastle United FC, partido 537800, NOT_FOUND)",
                "PREMIER: entradas del snapshot salteadas: TEAM_NOT_IN_CATALOG 1, PLAYER_NOT_IN_CATALOG 2");
    }

    @Test
    void sinNadaParaListarNoHayLineasDeDetalle(CapturedOutput output) {
        LeagueStatsResult clean = new LeagueStatsResult(League.SERIE_A, LeagueStatsStatus.PROCESSED, 0, 10,
                List.of(), List.of(), List.of(), Map.of());
        StatsSyncReport report = report(null, List.of(clean));

        logger.log(report);

        assertThat(output).contains("SERIE_A: procesada.")
                .doesNotContain("SERIE_A: partidos omitidos", "SERIE_A: equipos sin cruzar",
                        "SERIE_A: jugadores sin cruzar", "SERIE_A: entradas del snapshot salteadas");
    }

    @Test
    void laLigaCortadaVaEnWarnYLaNoAlcanzadaEnInfo(CapturedOutput output) {
        LeagueStatsResult cut = new LeagueStatsResult(League.PREMIER, LeagueStatsStatus.CUT, 0, 4, List.of(),
                List.of(), List.of(), Map.of());
        LeagueStatsResult notReached = LeagueStatsResult.notReached(League.BUNDESLIGA, 5, Map.of());
        StatsSyncReport report = report(matchCut(), List.of(cut, notReached));

        logger.log(report);

        assertThat(lineWith(output, "PREMIER: cortada por bloqueo.")).contains("WARN");
        assertThat(lineWith(output, "BUNDESLIGA: no alcanzada por el corte.")).contains("INFO")
                .contains("Partidos: 5 desde el snapshot y 0 por scraping");
    }

    @Test
    void laLineaFinalTraeLaDuracionLosTotalesYSinCorte(CapturedOutput output) {
        StatsSyncReport report = report(null, List.of(processedPremier()));

        logger.log(report);

        assertThat(lineWith(output, "Sincronización de WhoScored")).contains("INFO").endsWith(
                "Sincronización de WhoScored FULL (WEEKLY) terminada en 2830 s: 62 partidos procesados "
                        + "(2 desde el snapshot y 60 por scraping), 1 omitidos, sin corte.");
    }

    @Test
    void laLineaFinalDeUnaCortadaTraeLaPaginaLaLigaYElPartido(CapturedOutput output) {
        StatsSyncReport report = report(matchCut(), List.of());

        logger.log(report);

        assertThat(output).contains("0 omitidos, cortada por bloqueo en la página /matches/1983620/live "
                + "(PREMIER, partido 560620).");
    }

    @Test
    void unCorteEnLaPaginaDeLaLigaNoNombraNingunPartido(CapturedOutput output) {
        RunCut leagueCut = new RunCut(WhoScoredPages.league(League.PREMIER), League.PREMIER, null);
        StatsSyncReport report = report(leagueCut, List.of());

        logger.log(report);

        assertThat(output).contains("cortada por bloqueo en la página "
                + "/regions/252/tournaments/2/england-premier-league (PREMIER).");
    }

    private static LeagueStatsResult processedPremier() {
        return new LeagueStatsResult(League.PREMIER, LeagueStatsStatus.PROCESSED, 2, 60,
                List.of(new SkippedStatsMatch("560603", "Everton FC", "Manchester United FC", STARTED_AT,
                        StatsSkipReason.NOT_IN_SOURCE)),
                List.of(new UncrossedTeam(MAN_UTD.externalId(), MAN_UTD.name(), CrossFailure.NOT_FOUND)),
                List.of(new UncrossedPlayer("401234", "Mason Miley", "Newcastle United FC", "537800",
                        CrossFailure.NOT_FOUND)),
                Map.of(SnapshotSkipReason.TEAM_NOT_IN_CATALOG, 1, SnapshotSkipReason.PLAYER_NOT_IN_CATALOG, 2));
    }

    private static RunCut matchCut() {
        return new RunCut(WhoScoredPages.match("1983620"), League.PREMIER, "560620");
    }

    private static StatsSyncReport report(RunCut cut, List<LeagueStatsResult> leagues) {
        return new StatsSyncReport(SyncType.FULL, SyncOrigin.WEEKLY, STARTED_AT, FINISHED_AT, SnapshotStatus.LOADED,
                cut, leagues);
    }

    private static String lineWith(CapturedOutput output, String text) {
        return output.getAll().lines().filter(line -> line.contains(text)).findFirst().orElseThrow();
    }
}
