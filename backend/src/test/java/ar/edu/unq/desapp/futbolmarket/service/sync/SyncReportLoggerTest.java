package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.TIMED_KICK_OFF;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedTeam;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.season;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.seasonId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.tsimikas;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.DuplicatedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.EntityCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PlayerSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

@ExtendWith(OutputCaptureExtension.class)
class SyncReportLoggerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(41);
    private static final String TIMEOUT_REASON = "La fuente no respondió a tiempo.";
    private static final String NO_INACTIVATION = "No se inactivó a nadie";

    private final SyncReportLogger reportLogger = new SyncReportLogger();
    private final Team savedLiverpool = savedTeam(1L, liverpool(), League.PREMIER);

    @Test
    void unaLigaProcesadaVaEnInfoConSusConteosYSuTemporadaYUnaFallidaEnWarnConSuMotivo(CapturedOutput output) {
        SyncReport report = fullReport(false, List.of(
                premier(List.of(), List.of(), List.of()),
                LeagueSyncResult.failed(League.BUNDESLIGA, TIMEOUT_REASON)));

        reportLogger.log(report);

        assertLine(output, "PREMIER: procesada.")
                .contains("INFO")
                .contains("Equipos 2/0/0, jugadores 8/0/1, partidos 2/0/0 (creados/actualizados/omitidos).")
                .contains("Temporada 2502: 2026-08-21 a 2027-05-30, jornada 6.");
        assertLine(output, "BUNDESLIGA: fallida.")
                .contains("WARN")
                .contains("Motivo: " + TIMEOUT_REASON);
    }

    @Test
    void lasLineasDeDetalleListanOmitidosDuplicadosReactivadosEInactivadosConSuNombreYSuEquipo(
            CapturedOutput output) {
        SkippedPlayer skippedPlayer = new SkippedPlayer(NO_POSITION_PLAYER_ID, NO_POSITION_PLAYER_NAME, CHELSEA_NAME,
                PlayerSkipReason.MISSING_POSITION);
        SkippedMatch skippedMatch = new SkippedMatch("250291", LIVERPOOL_ID, "1076", TIMED_KICK_OFF,
                MatchSkipReason.UNKNOWN_TEAM);
        Player reactivated = savedPlayer(10L, alisson(), savedLiverpool);
        Player inactivated = savedPlayer(11L, tsimikas(), savedLiverpool).deactivate();
        DuplicatedPlayer duplicated = new DuplicatedPlayer(CHIESA_ID, "Federico Chiesa", LIVERPOOL_NAME, JUVENTUS_NAME);
        SyncReport report = new SyncReport(SyncType.FULL, SyncOrigin.WEEKLY, STARTED_AT, FINISHED_AT,
                List.of(premier(List.of(skippedPlayer), List.of(skippedMatch), List.of(reactivated))),
                List.of(duplicated), List.of(inactivated), true);

        reportLogger.log(report);

        assertLine(output, "PREMIER: jugadores omitidos:")
                .contains("INFO")
                .contains("Mahdi Nicoll-Jazuli (Chelsea FC, MISSING_POSITION)");
        assertLine(output, "PREMIER: partidos omitidos:").contains("250291 (64 vs 1076, UNKNOWN_TEAM)");
        assertLine(output, "PREMIER: jugadores reactivados:").contains("Alisson Becker (Liverpool FC)");
        assertLine(output, "Jugadores en más de un plantel:")
                .contains("Federico Chiesa (queda en Liverpool FC; se ignora Juventus FC)");
        assertLine(output, "Jugadores inactivados:").contains("Kostas Tsimikas (Liverpool FC)");
    }

    @Test
    void unJugadorOmitidoSinNombreSeIdentificaPorSuIdDeLaFuente(CapturedOutput output) {
        SkippedPlayer withoutName = new SkippedPlayer("64099", null, LIVERPOOL_NAME, PlayerSkipReason.MISSING_NAME);
        SyncReport report = fullReport(true, List.of(premier(List.of(withoutName), List.of(), List.of())));

        reportLogger.log(report);

        assertLine(output, "PREMIER: jugadores omitidos:").contains("jugador 64099 (Liverpool FC, MISSING_NAME)");
    }

    @Test
    void sinNadaParaListarNoHayLineasDeDetalle(CapturedOutput output) {
        SyncReport report = fullReport(true, List.of(premier(List.of(), List.of(), List.of())));

        reportLogger.log(report);

        assertThat(output.getAll())
                .contains("PREMIER: procesada.")
                .doesNotContain("omitidos:")
                .doesNotContain("reactivados:")
                .doesNotContain("más de un plantel:")
                .doesNotContain("Jugadores inactivados:");
    }

    @Test
    void laLineaFinalTraeLaDuracionYLosTotalesYNoDaMotivoSiSeInactivo(CapturedOutput output) {
        SyncReport report = fullReport(true, List.of(premier(List.of(), List.of(), List.of()),
                processed(League.BUNDESLIGA), processed(League.LA_LIGA), processed(League.SERIE_A),
                processed(League.LIGUE_1)));

        reportLogger.log(report);

        assertLine(output, "terminada en")
                .contains("INFO")
                .contains("Sincronización FULL (WEEKLY) terminada en 41 s: 5 ligas procesadas, 0 fallidas, "
                        + "0 jugadores inactivados.")
                .doesNotContain(NO_INACTIVATION);
    }

    @Test
    void laLineaFinalDiceQueNoSeInactivoPorSerDeUnaSolaLiga(CapturedOutput output) {
        SyncReport report = new SyncReport(SyncType.SINGLE_LEAGUE, SyncOrigin.MANUAL, STARTED_AT, FINISHED_AT,
                List.of(processed(League.SERIE_A)), List.of(), List.of(), false);

        reportLogger.log(report);

        assertLine(output, "terminada en")
                .contains("1 ligas procesadas, 0 fallidas, 0 jugadores inactivados.")
                .contains(NO_INACTIVATION + ": fue una sincronización de una sola liga.");
    }

    @Test
    void laLineaFinalDiceQueNoSeInactivoPorLasLigasFallidas(CapturedOutput output) {
        SyncReport report = fullReport(false, List.of(premier(List.of(), List.of(), List.of()),
                LeagueSyncResult.failed(League.BUNDESLIGA, TIMEOUT_REASON), processed(League.LA_LIGA),
                processed(League.SERIE_A), processed(League.LIGUE_1)));

        reportLogger.log(report);

        assertLine(output, "terminada en")
                .contains("4 ligas procesadas, 1 fallidas, 0 jugadores inactivados.")
                .contains(NO_INACTIVATION + ": hubo ligas fallidas.");
    }

    /**
     * La línea del registro que contiene el fragmento. Tiene que haber exactamente una.
     */
    private static AbstractStringAssert<?> assertLine(CapturedOutput output, String fragment) {
        return assertThat(output.getAll().lines().filter(line -> line.contains(fragment)).toList())
                .singleElement(InstanceOfAssertFactories.STRING);
    }

    private static SyncReport fullReport(boolean inactivationApplied, List<LeagueSyncResult> leagues) {
        return new SyncReport(SyncType.FULL, SyncOrigin.WEEKLY, STARTED_AT, FINISHED_AT, leagues, List.of(), List.of(),
                inactivationApplied);
    }

    private static LeagueSyncResult premier(List<SkippedPlayer> skippedPlayers, List<SkippedMatch> skippedMatches,
                                            List<Player> reactivated) {
        return new LeagueSyncResult(League.PREMIER, LeagueSyncStatus.SUCCEEDED, null,
                season(League.PREMIER, seasonId(League.PREMIER)), new EntityCounts(2, 0, 0), new EntityCounts(8, 0, 1),
                new EntityCounts(2, 0, 0), skippedPlayers, skippedMatches, reactivated);
    }

    private static LeagueSyncResult processed(League league) {
        return new LeagueSyncResult(league, LeagueSyncStatus.SUCCEEDED, null, season(league, seasonId(league)),
                EntityCounts.none(), EntityCounts.none(), EntityCounts.none(), List.of(), List.of(), List.of());
    }
}
