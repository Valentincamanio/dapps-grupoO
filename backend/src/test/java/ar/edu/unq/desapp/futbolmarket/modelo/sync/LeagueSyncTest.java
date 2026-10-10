package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.ALISSON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_CREST;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CURRENT_MATCHDAY;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.FINISHED_KICK_OFF;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.JUVENTUS_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_CREST;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.NO_POSITION_PLAYER_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.PREMIER_SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.SEASON_END;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.SEASON_START;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.TIMED_KICK_OFF;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chelsea;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.finishedMatchId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.match;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.noPositionPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.player;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedPlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.savedTeam;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.timedMatchId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class LeagueSyncTest {

    private final LeagueSnapshot premier = snapshot(League.PREMIER);
    private final Team savedLiverpool = savedTeam(1L, liverpool(), League.PREMIER);
    private final Team savedChelsea = savedTeam(2L, chelsea(), League.PREMIER);
    private final Season savedSeason = new Season(
            3L, PREMIER_SEASON_ID, League.PREMIER, SEASON_START, SEASON_END, CURRENT_MATCHDAY);

    @Test
    void actualizaLosEquiposGuardadosYCreaLosNuevosConSusConteos() {
        LeagueSync sync = syncOf(premier);
        Team storedLiverpool = new Team(1L, LIVERPOOL_ID, "Liverpool", null, League.PREMIER);

        List<Team> toSave = sync.teamsToSave(List.of(storedLiverpool));

        assertThat(toSave).containsExactly(
                new Team(1L, LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, League.PREMIER),
                new Team(null, CHELSEA_ID, CHELSEA_NAME, CHELSEA_CREST, League.PREMIER));
        assertThat(sync.result().teams()).isEqualTo(new EntityCounts(1, 1, 0));
    }

    @Test
    void unJugadorNuevoCompletoSeCreaActivoEnSuEquipo() {
        LeagueSync sync = syncOf(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson())));

        List<Player> toSave = sync.playersToSave(List.of(), List.of(savedLiverpool));

        assertThat(toSave).containsExactly(alisson().toNewPlayer(savedLiverpool));
        assertThat(toSave.getFirst().active()).isTrue();
        assertThat(sync.result().players()).isEqualTo(new EntityCounts(1, 0, 0));
    }

    @Test
    void unJugadorNuevoSinNombreOSinPosicionSeOmiteConSuMotivoYSuEquipo() {
        LeagueSync sync = syncOf(snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson(), player("64099", "  ", Position.GOALKEEPER)),
                team(CHELSEA_ID, CHELSEA_NAME, noPositionPlayer())));

        List<Player> toSave = sync.playersToSave(List.of(), List.of(savedLiverpool, savedChelsea));
        LeagueSyncResult result = sync.result();

        assertThat(toSave).extracting(Player::externalId).containsExactly(ALISSON_ID);
        assertThat(result.skippedPlayers()).containsExactly(
                new SkippedPlayer("64099", null, LIVERPOOL_NAME, PlayerSkipReason.MISSING_NAME),
                new SkippedPlayer(NO_POSITION_PLAYER_ID, NO_POSITION_PLAYER_NAME, CHELSEA_NAME,
                        PlayerSkipReason.MISSING_POSITION));
        assertThat(result.players()).isEqualTo(new EntityCounts(1, 0, 2));
    }

    @Test
    void unJugadorGuardadoQueLlegaSinPosicionConservaLaSuyaYCuentaComoActualizado() {
        LeagueSync sync = syncOf(snapshot(League.PREMIER, team(CHELSEA_ID, CHELSEA_NAME, noPositionPlayer())));
        Player stored = new Player(20L, NO_POSITION_PLAYER_ID, NO_POSITION_PLAYER_NAME, Position.MIDFIELDER,
                savedChelsea);

        List<Player> toSave = sync.playersToSave(List.of(stored), List.of(savedChelsea));
        LeagueSyncResult result = sync.result();

        assertThat(toSave).singleElement().satisfies(player -> {
            assertThat(player.id()).isEqualTo(20L);
            assertThat(player.position()).isEqualTo(Position.MIDFIELDER);
        });
        assertThat(result.players()).isEqualTo(new EntityCounts(0, 1, 0));
        assertThat(result.skippedPlayers()).isEmpty();
    }

    @Test
    void unJugadorGuardadoInactivoQuedaActivoYFiguraEntreLosReactivados() {
        LeagueSync sync = syncOf(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson())));
        Player inactive = savedPlayer(11L, alisson(), savedLiverpool).deactivate();

        List<Player> toSave = sync.playersToSave(List.of(inactive), List.of(savedLiverpool));

        assertThat(toSave).singleElement().satisfies(player -> assertThat(player.active()).isTrue());
        assertThat(sync.result().reactivatedPlayers()).containsExactlyElementsOf(toSave);
    }

    @Test
    void unJugadorTransferidoDesdeUnEquipoDeOtraLigaQuedaEnElEquipoNuevo() {
        Team savedJuventus = savedTeam(5L, team(JUVENTUS_ID, JUVENTUS_NAME), League.SERIE_A);
        Player storedInJuventus = savedPlayer(12L, chiesa(), savedJuventus);
        LeagueSync sync = syncOf(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa())));

        List<Player> toSave = sync.playersToSave(List.of(storedInJuventus), List.of(savedLiverpool));

        assertThat(toSave).singleElement().satisfies(player -> {
            assertThat(player.id()).isEqualTo(12L);
            assertThat(player.team()).isEqualTo(savedLiverpool);
            assertThat(player.league()).isEqualTo(League.PREMIER);
        });
    }

    @Test
    void unaAparicionQueLaAsignacionNoConservaNoSeEscribeNiSeCuenta() {
        LeagueSnapshot withChiesaTwice = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, chiesa()),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa()));
        Player storedInChelsea = savedPlayer(12L, chiesa(), savedChelsea);
        SquadAssignment assignment = SquadAssignment.of(List.of(withChiesaTwice)).resolve(List.of(storedInChelsea));
        LeagueSync sync = new LeagueSync(withChiesaTwice, assignment);

        List<Player> toSave = sync.playersToSave(List.of(storedInChelsea), List.of(savedLiverpool, savedChelsea));

        assertThat(toSave).singleElement().satisfies(player -> assertThat(player.team()).isEqualTo(savedChelsea));
        assertThat(sync.result().players()).isEqualTo(new EntityCounts(0, 1, 0));
    }

    @Test
    void unJugadorRepetidoEnElMismoEquipoSeEscribeUnaSolaVez() {
        LeagueSync sync = syncOf(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson(), alisson())));

        List<Player> toSave = sync.playersToSave(List.of(), List.of(savedLiverpool));

        assertThat(toSave).extracting(Player::externalId).containsExactly(ALISSON_ID);
        assertThat(sync.result().players()).isEqualTo(new EntityCounts(1, 0, 0));
    }

    @Test
    void laTemporadaGuardadaSeActualizaConLasFechasYLaJornadaDeLaFuente() {
        LeagueSync sync = syncOf(premier);
        Season stored = new Season(7L, PREMIER_SEASON_ID, League.PREMIER, SEASON_START, SEASON_END, 5);

        Season toSave = sync.seasonToSave(Optional.of(stored));

        assertThat(toSave).isEqualTo(
                new Season(7L, PREMIER_SEASON_ID, League.PREMIER, SEASON_START, SEASON_END, CURRENT_MATCHDAY));
    }

    @Test
    void unaTemporadaNuevaSeCreaConLaDelSnapshot() {
        LeagueSync sync = syncOf(premier);

        Season toSave = sync.seasonToSave(Optional.empty());

        assertThat(toSave).isEqualTo(premier.season());
        assertThat(toSave.id()).isNull();
    }

    @Test
    void unPartidoConEstadoDesconocidoOConUnEquipoFueraDelCatalogoSeOmiteSinHacerFallarLaLiga() {
        MatchSnapshot unknownStatus = match("250290", PREMIER_SEASON_ID, null, LIVERPOOL_ID, CHELSEA_ID);
        MatchSnapshot unknownTeam = match("250291", PREMIER_SEASON_ID, MatchStatus.TIMED, LIVERPOOL_ID, "1076");
        MatchSnapshot both = match("250292", PREMIER_SEASON_ID, null, "1076", LIVERPOOL_ID);
        LeagueSync sync = syncOf(snapshot(League.PREMIER, List.of(liverpool(), chelsea()),
                List.of(unknownStatus, unknownTeam, both)));

        List<Match> toSave = sync.matchesToSave(List.of(), savedSeason, List.of(savedLiverpool, savedChelsea));
        LeagueSyncResult result = sync.result();

        assertThat(toSave).isEmpty();
        assertThat(result.skippedMatches()).containsExactly(
                new SkippedMatch("250290", LIVERPOOL_ID, CHELSEA_ID, TIMED_KICK_OFF, MatchSkipReason.UNKNOWN_STATUS),
                new SkippedMatch("250291", LIVERPOOL_ID, "1076", TIMED_KICK_OFF, MatchSkipReason.UNKNOWN_TEAM),
                new SkippedMatch("250292", "1076", LIVERPOOL_ID, TIMED_KICK_OFF, MatchSkipReason.UNKNOWN_STATUS));
        assertThat(result.matches()).isEqualTo(new EntityCounts(0, 0, 3));
        assertThat(result.status()).isEqualTo(LeagueSyncStatus.SUCCEEDED);
    }

    @Test
    void unPartidoGuardadoSeActualizaYUnoNuevoSeCrea() {
        LeagueSync sync = syncOf(premier);
        Match storedPostponed = new Match(30L, timedMatchId(League.PREMIER), savedSeason,
                TIMED_KICK_OFF.minus(Duration.ofDays(7)), CURRENT_MATCHDAY, MatchStatus.POSTPONED, savedChelsea,
                savedLiverpool, null, null, null);

        List<Match> toSave = sync.matchesToSave(List.of(storedPostponed), savedSeason,
                List.of(savedLiverpool, savedChelsea));

        assertThat(toSave)
                .extracting(Match::id, Match::externalId, Match::status, Match::utcDate)
                .containsExactly(
                        tuple(null, finishedMatchId(League.PREMIER), MatchStatus.FINISHED,
                                FINISHED_KICK_OFF),
                        tuple(30L, timedMatchId(League.PREMIER), MatchStatus.TIMED, TIMED_KICK_OFF));
        assertThat(sync.result().matches()).isEqualTo(new EntityCounts(1, 1, 0));
    }

    @Test
    void elResultadoTraeLosConteosLasListasYLaTemporadaGuardada() {
        LeagueSync sync = syncOf(premier);
        List<Team> savedTeams = List.of(savedLiverpool, savedChelsea);

        sync.teamsToSave(List.of());
        sync.playersToSave(List.of(), savedTeams);
        sync.matchesToSave(List.of(), savedSeason, savedTeams);
        LeagueSyncResult result = sync.result();

        assertThat(result.league()).isEqualTo(League.PREMIER);
        assertThat(result.status()).isEqualTo(LeagueSyncStatus.SUCCEEDED);
        assertThat(result.failureReason()).isNull();
        assertThat(result.season()).isEqualTo(savedSeason);
        assertThat(result.teams()).isEqualTo(new EntityCounts(2, 0, 0));
        assertThat(result.players()).isEqualTo(new EntityCounts(8, 0, 1));
        assertThat(result.matches()).isEqualTo(new EntityCounts(2, 0, 0));
        assertThat(result.skippedPlayers()).extracting(SkippedPlayer::externalId)
                .containsExactly(NO_POSITION_PLAYER_ID);
        assertThat(result.skippedMatches()).isEmpty();
        assertThat(result.reactivatedPlayers()).isEmpty();
    }

    private static LeagueSync syncOf(LeagueSnapshot snapshot) {
        return new LeagueSync(snapshot, SquadAssignment.of(List.of(snapshot)));
    }
}
