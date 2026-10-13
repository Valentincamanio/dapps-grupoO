package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.allSnapshots;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.player;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedTeam;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

class SquadAssignmentTest {

    private static final String CHIESA_NAME = "Federico Chiesa";

    @Test
    void sinDuplicadosConservaTodasLasApariciones() {
        List<LeagueSnapshot> snapshots = allSnapshots();

        SquadAssignment assignment = SquadAssignment.of(snapshots);

        assertThat(assignment.duplicatedPlayerExternalIds()).isEmpty();
        assertThat(assignment.duplicates()).isEmpty();
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.teams()).allSatisfy(team ->
                assertThat(team.squad()).allSatisfy(player ->
                        assertThat(assignment.keeps(player.externalId(), team.externalId())).isTrue())));
    }

    @Test
    void detectaUnJugadorEnDosEquiposDeLaMismaLiga() {
        LeagueSnapshot premier = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson(), chiesa()),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa()));

        SquadAssignment assignment = SquadAssignment.of(List.of(premier));

        assertThat(assignment.duplicatedPlayerExternalIds()).containsExactly(CHIESA_ID);
    }

    @Test
    void detectaUnJugadorEnEquiposDeDosLigas() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(chiesa(), chiesa());

        SquadAssignment assignment = SquadAssignment.of(snapshots);

        assertThat(assignment.duplicatedPlayerExternalIds()).containsExactly(CHIESA_ID);
    }

    @Test
    void unJugadorGuardadoConservaSuEquipoActualAunqueNoSeaElPrimero() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(chiesa(), chiesa());
        Player current = savedPlayer(10L, chiesa(), savedTeam(3L, team(JUVENTUS_ID, JUVENTUS_NAME), League.SERIE_A));

        SquadAssignment assignment = SquadAssignment.of(snapshots).resolve(List.of(current));

        assertThat(assignment.keeps(CHIESA_ID, JUVENTUS_ID)).isTrue();
        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isFalse();
    }

    @Test
    void unJugadorGuardadoCuyoEquipoNoFueInformadoQuedaEnElPrimeroAunqueLlegueSinPosicion() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(withoutPosition(), chiesa());
        Player current = savedPlayer(10L, chiesa(), savedTeam(2L, team(CHELSEA_ID, CHELSEA_NAME), League.PREMIER));

        SquadAssignment assignment = SquadAssignment.of(snapshots).resolve(List.of(current));

        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isTrue();
        assertThat(assignment.keeps(CHIESA_ID, JUVENTUS_ID)).isFalse();
    }

    @Test
    void unJugadorNuevoCompletoQuedaEnLaPrimeraAparicion() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(chiesa(), chiesa());

        SquadAssignment assignment = SquadAssignment.of(snapshots).resolve(List.of());

        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isTrue();
        assertThat(assignment.keeps(CHIESA_ID, JUVENTUS_ID)).isFalse();
    }

    @Test
    void unJugadorNuevoQueLlegaSinPosicionEnLaPrimeraAparicionQuedaEnLaSegundaQueEstaCompleta() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(withoutPosition(), chiesa());

        SquadAssignment assignment = SquadAssignment.of(snapshots).resolve(List.of());

        assertThat(assignment.keeps(CHIESA_ID, JUVENTUS_ID)).isTrue();
        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isFalse();
    }

    @Test
    void unJugadorNuevoSinNingunaAparicionCompletaQuedaEnLaPrimera() {
        List<LeagueSnapshot> snapshots = chiesaInLiverpoolAndJuventus(withoutPosition(),
                player(CHIESA_ID, null, Position.FORWARD));

        SquadAssignment assignment = SquadAssignment.of(snapshots).resolve(List.of());

        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isTrue();
        assertThat(assignment.keeps(CHIESA_ID, JUVENTUS_ID)).isFalse();
    }

    @Test
    void duplicatesListaCadaAparicionIgnoradaConElEquipoQueQuedaYElIgnorado() {
        LeagueSnapshot premier = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa()),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa()));
        LeagueSnapshot serieA = snapshot(League.SERIE_A, team(JUVENTUS_ID, JUVENTUS_NAME, chiesa()));

        SquadAssignment assignment = SquadAssignment.of(List.of(premier, serieA)).resolve(List.of());

        assertThat(assignment.duplicates()).containsExactly(
                new DuplicatedPlayer(CHIESA_ID, CHIESA_NAME, LIVERPOOL_NAME, CHELSEA_NAME),
                new DuplicatedPlayer(CHIESA_ID, CHIESA_NAME, LIVERPOOL_NAME, JUVENTUS_NAME));
    }

    @Test
    void elMismoJugadorDosVecesEnUnEquipoNoEsUnDuplicado() {
        LeagueSnapshot premier = snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa(), chiesa()));

        SquadAssignment assignment = SquadAssignment.of(List.of(premier));

        assertThat(assignment.duplicatedPlayerExternalIds()).isEmpty();
        assertThat(assignment.duplicates()).isEmpty();
        assertThat(assignment.keeps(CHIESA_ID, LIVERPOOL_ID)).isTrue();
    }

    /**
     * La Premier se procesa antes que la Serie A, así que Liverpool FC es la primera aparición.
     */
    private static List<LeagueSnapshot> chiesaInLiverpoolAndJuventus(PlayerSnapshot inLiverpool,
                                                                     PlayerSnapshot inJuventus) {
        return List.of(
                snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson(), inLiverpool)),
                snapshot(League.SERIE_A, team(JUVENTUS_ID, JUVENTUS_NAME, inJuventus)));
    }

    private static PlayerSnapshot withoutPosition() {
        return player(CHIESA_ID, CHIESA_NAME, null);
    }
}
