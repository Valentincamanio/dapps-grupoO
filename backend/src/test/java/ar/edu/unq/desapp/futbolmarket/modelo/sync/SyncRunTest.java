package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.allSnapshots;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chelsea;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.noPositionPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.player;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedTeam;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.season;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.seasonId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class SyncRunTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-12T07:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(42);
    private static final String DOWNLOAD_FAILURE = "La fuente no respondió a tiempo.";
    private static final String WRITE_FAILURE = "No se pudieron guardar los datos de la liga.";

    private final Team savedLiverpool = savedTeam(1L, liverpool(), League.PREMIER);
    private final Player unseen = new Player(99L, "999999", "Jugador Retirado", Position.FORWARD, savedLiverpool);

    @Test
    void unaCompletaPideLasCincoLigasEnElOrdenDelEnum() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);

        assertThat(run.leagues()).containsExactly(League.values());
    }

    @Test
    void unaDeUnaSolaLigaPideEsaLigaYEsManual() {
        SyncRun run = SyncRun.singleLeague(League.SERIE_A, STARTED_AT);

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(run.leagues()).containsExactly(League.SERIE_A);
        assertThat(report.type()).isEqualTo(SyncType.SINGLE_LEAGUE);
        assertThat(report.origin()).isEqualTo(SyncOrigin.MANUAL);
    }

    @Test
    void unaCompletaConLasCincoLigasEnExitoPuedeInactivar() {
        SyncRun run = SyncRun.full(SyncOrigin.MANUAL, STARTED_AT);

        run.registerSnapshots(allSnapshots());
        recordAllSucceeded(run);

        assertThat(run.canDeactivate()).isTrue();
    }

    @Test
    void unaCompletaConUnaLigaQueFalloAlDescargarNoPuedeInactivar() {
        SyncRun run = SyncRun.full(SyncOrigin.MANUAL, STARTED_AT);

        run.registerSnapshots(allSnapshotsBut(League.BUNDESLIGA));
        recordAllSucceededBut(run, League.BUNDESLIGA);
        run.recordFailure(League.BUNDESLIGA, DOWNLOAD_FAILURE);

        assertThat(run.canDeactivate()).isFalse();
    }

    @Test
    void unaCompletaConUnaLigaQueFalloAlEscribirNoPuedeInactivar() {
        SyncRun run = SyncRun.full(SyncOrigin.MANUAL, STARTED_AT);

        run.registerSnapshots(allSnapshots());
        recordAllSucceededBut(run, League.LIGUE_1);
        run.recordFailure(League.LIGUE_1, WRITE_FAILURE);

        assertThat(run.canDeactivate()).isFalse();
    }

    @Test
    void unaDeUnaSolaLigaNoPuedeInactivarAunqueTermineBien() {
        SyncRun run = SyncRun.singleLeague(League.PREMIER, STARTED_AT);

        run.registerSnapshots(List.of(snapshot(League.PREMIER)));
        run.recordSuccess(succeeded(League.PREMIER));

        assertThat(run.canDeactivate()).isFalse();
    }

    @Test
    void playersToDeactivateDevuelveLosActivosQueNoAparecieronEnNingunPlantel() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshots());
        recordAllSucceeded(run);
        Player seen = savedPlayer(10L, alisson(), savedLiverpool);

        List<Player> toDeactivate = run.playersToDeactivate(List.of(seen, unseen));

        assertThat(toDeactivate).containsExactly(unseen);
    }

    @Test
    void playersToDeactivateDevuelveUnaListaVaciaSiNoSePuedeInactivar() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshotsBut(League.SERIE_A));
        recordAllSucceededBut(run, League.SERIE_A);
        run.recordFailure(League.SERIE_A, DOWNLOAD_FAILURE);

        List<Player> toDeactivate = run.playersToDeactivate(List.of(unseen));

        assertThat(toDeactivate).isEmpty();
    }

    @Test
    void losOmitidosLosDuplicadosYLosSinPosicionCuentanComoVistos() {
        LeagueSnapshot premier = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa(), player("64099", null, Position.GOALKEEPER)),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa(), noPositionPlayer()));
        List<LeagueSnapshot> snapshots = new ArrayList<>(allSnapshotsBut(League.PREMIER));
        snapshots.addFirst(premier);
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(snapshots);
        recordAllSucceeded(run);
        Team savedChelsea = savedTeam(2L, chelsea(), League.PREMIER);
        List<Player> active = List.of(
                savedPlayer(10L, chiesa(), savedLiverpool),
                new Player(11L, "64099", "Arquero Sin Nombre En La Fuente", Position.GOALKEEPER, savedLiverpool),
                new Player(12L, NO_POSITION_PLAYER_ID, NO_POSITION_PLAYER_NAME, Position.MIDFIELDER, savedChelsea),
                unseen);

        List<Player> toDeactivate = run.playersToDeactivate(active);

        assertThat(toDeactivate).containsExactly(unseen);
    }

    @Test
    void elInformeOrdenaLasLigasPorElEnumYMarcaQueSeAplicoLaInactivacion() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshots());
        List.of(League.LIGUE_1, League.SERIE_A, League.LA_LIGA, League.BUNDESLIGA, League.PREMIER)
                .forEach(league -> run.recordSuccess(succeeded(league)));

        SyncReport report = run.finish(FINISHED_AT, List.of(unseen));

        assertThat(report.leagues()).extracting(LeagueSyncResult::league).containsExactly(League.values());
        assertThat(report.inactivationApplied()).isTrue();
        assertThat(report.inactivatedPlayers()).containsExactly(unseen);
        assertThat(report.type()).isEqualTo(SyncType.FULL);
        assertThat(report.origin()).isEqualTo(SyncOrigin.WEEKLY);
        assertThat(report.startedAt()).isEqualTo(STARTED_AT);
        assertThat(report.finishedAt()).isEqualTo(FINISHED_AT);
    }

    @Test
    void elInformeIncluyeLasAparicionesIgnoradasDeLosDuplicados() {
        LeagueSnapshot premier = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa()),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa()));
        SyncRun run = SyncRun.singleLeague(League.PREMIER, STARTED_AT);
        run.registerSnapshots(List.of(premier));
        run.resolveDuplicates(List.of());

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(run.duplicatedPlayerExternalIds()).containsExactly(CHIESA_ID);
        assertThat(report.duplicatedPlayers()).containsExactly(
                new DuplicatedPlayer(CHIESA_ID, "Federico Chiesa", LIVERPOOL_NAME, CHELSEA_NAME));
    }

    @Test
    void elInformeNoTraeMotivoDeNoInactivacionSiSeInactivo() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshots());
        recordAllSucceeded(run);

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.inactivationSkipReason()).isEmpty();
    }

    @Test
    void elInformeDeUnaSolaLigaDiceQueNoSeInactivoPorSerDeUnaSolaLiga() {
        SyncRun run = SyncRun.singleLeague(League.PREMIER, STARTED_AT);
        run.registerSnapshots(List.of(snapshot(League.PREMIER)));
        run.recordSuccess(succeeded(League.PREMIER));

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.inactivationApplied()).isFalse();
        assertThat(report.inactivationSkipReason()).contains(InactivationSkipReason.SINGLE_LEAGUE);
    }

    @Test
    void elInformeDeUnaCompletaConUnaLigaFallidaDiceQueNoSeInactivoPorLasLigasFallidas() {
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, STARTED_AT);
        run.registerSnapshots(allSnapshotsBut(League.LA_LIGA));
        recordAllSucceededBut(run, League.LA_LIGA);
        run.recordFailure(League.LA_LIGA, DOWNLOAD_FAILURE);

        SyncReport report = run.finish(FINISHED_AT, List.of());

        assertThat(report.inactivationApplied()).isFalse();
        assertThat(report.inactivationSkipReason()).contains(InactivationSkipReason.FAILED_LEAGUES);
        assertThat(report.failedLeagues()).singleElement().satisfies(result -> {
            assertThat(result.league()).isEqualTo(League.LA_LIGA);
            assertThat(result.failureReason()).isEqualTo(DOWNLOAD_FAILURE);
        });
    }

    private static List<LeagueSnapshot> allSnapshotsBut(League excluded) {
        return allSnapshots().stream().filter(snapshot -> snapshot.league() != excluded).toList();
    }

    private static void recordAllSucceeded(SyncRun run) {
        run.leagues().forEach(league -> run.recordSuccess(succeeded(league)));
    }

    private static void recordAllSucceededBut(SyncRun run, League excluded) {
        run.leagues().stream()
                .filter(league -> league != excluded)
                .forEach(league -> run.recordSuccess(succeeded(league)));
    }

    private static LeagueSyncResult succeeded(League league) {
        return new LeagueSyncResult(league, LeagueSyncStatus.SUCCEEDED, null, season(league, seasonId(league)),
                EntityCounts.none(), EntityCounts.none(), EntityCounts.none(), List.of(), List.of(), List.of());
    }
}
