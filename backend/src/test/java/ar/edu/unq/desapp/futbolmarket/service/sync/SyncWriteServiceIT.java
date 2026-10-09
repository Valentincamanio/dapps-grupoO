package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.ALISSON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.BAYERN_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.BAYERN_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_CREST;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CURRENT_MATCHDAY;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.FINISHED_KICK_OFF;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_CREST;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.PREMIER_SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.SEASON_END;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.SEASON_START;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.TIMED_KICK_OFF;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.TSIMIKAS_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.allSnapshots;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chelsea;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.finishedMatch;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.finishedMatchId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.match;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.player;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.timedMatchId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.withoutPlayer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.EntityCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncRun;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;

/**
 * La escritura de una liga contra la H2 {@code sync-it}, con la misma configuración que los IT de
 * temporadas y partidos para compartir el contexto (research D21).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sync-it;DB_CLOSE_DELAY=-1")
class SyncWriteServiceIT {
    private static final long PREMIER_TEAMS = 2;
    private static final long PREMIER_SAVED_PLAYERS = 8;
    private static final long PREMIER_MATCHES = 2;

    @Autowired
    private SyncWriteService writeService;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @BeforeEach
    void cleanDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }

    @Test
    void aplicarUnaLigaGuardaSusEquiposJugadoresTemporadaYPartidosYDevuelveLosCreados() {
        LeagueSnapshot premier = snapshot(League.PREMIER);
        SquadAssignment assignment = SquadAssignment.of(List.of(premier)).resolve(List.of());

        LeagueSyncResult result = writeService.applyLeague(premier, assignment);

        assertThat(teamDAO.count()).isEqualTo(PREMIER_TEAMS);
        assertThat(playerDAO.count()).isEqualTo(PREMIER_SAVED_PLAYERS);
        assertThat(seasonDAO.count()).isEqualTo(1);
        assertThat(matchDAO.count()).isEqualTo(PREMIER_MATCHES);
        assertThat(result.status()).isEqualTo(LeagueSyncStatus.SUCCEEDED);
        assertThat(result.teams()).isEqualTo(new EntityCounts(2, 0, 0));
        assertThat(result.players()).isEqualTo(new EntityCounts(8, 0, 1));
        assertThat(result.matches()).isEqualTo(new EntityCounts(2, 0, 0));
        assertThat(result.season().id()).isNotNull();
    }

    /**
     * El jugador sin posición sigue sin guardarse, así que la segunda vez también cuenta como omitido.
     */
    @Test
    void aplicarDosVecesElMismoSnapshotNoCreaNadaLaSegundaVezYCuentaTodoComoActualizado() {
        LeagueSnapshot premier = snapshot(League.PREMIER);
        SquadAssignment assignment = SquadAssignment.of(List.of(premier)).resolve(List.of());
        writeService.applyLeague(premier, assignment);

        LeagueSyncResult second = writeService.applyLeague(premier, assignment);

        assertThat(teamDAO.count()).isEqualTo(PREMIER_TEAMS);
        assertThat(playerDAO.count()).isEqualTo(PREMIER_SAVED_PLAYERS);
        assertThat(seasonDAO.count()).isEqualTo(1);
        assertThat(matchDAO.count()).isEqualTo(PREMIER_MATCHES);
        assertThat(second.teams()).isEqualTo(new EntityCounts(0, 2, 0));
        assertThat(second.players()).isEqualTo(new EntityCounts(0, 8, 1));
        assertThat(second.matches()).isEqualTo(new EntityCounts(0, 2, 0));
    }

    @Test
    void laTemporadaQuedaGuardadaConSuLigaSuInicioSuFinYSuJornadaActual() {
        LeagueSnapshot premier = snapshot(League.PREMIER);

        apply(premier);
        var saved = seasonRepository.findByExternalId(PREMIER_SEASON_ID);

        assertThat(saved).hasValueSatisfying(season -> {
            assertThat(season.league()).isEqualTo(League.PREMIER);
            assertThat(season.startDate()).isEqualTo(SEASON_START);
            assertThat(season.endDate()).isEqualTo(SEASON_END);
            assertThat(season.currentMatchday()).isEqualTo(CURRENT_MATCHDAY);
        });
    }

    @Test
    void todosLosPartidosQuedanGuardadosConFechaJornadaEstadoLocalYVisitante() {
        LeagueSnapshot premier = snapshot(League.PREMIER);

        apply(premier);
        List<Match> saved = matchRepository.findAllByExternalIds(premier.matchExternalIds());

        assertThat(saved)
                .extracting(Match::externalId, Match::utcDate, Match::matchday, Match::status,
                        match -> match.homeTeam().externalId(), match -> match.awayTeam().externalId())
                .containsExactlyInAnyOrder(
                        tuple(finishedMatchId(League.PREMIER), FINISHED_KICK_OFF, 1, MatchStatus.FINISHED,
                                LIVERPOOL_ID, CHELSEA_ID),
                        tuple(timedMatchId(League.PREMIER), TIMED_KICK_OFF, CURRENT_MATCHDAY + 1, MatchStatus.TIMED,
                                CHELSEA_ID, LIVERPOOL_ID));
        assertThat(saved).allSatisfy(match ->
                assertThat(match.season().externalId()).isEqualTo(PREMIER_SEASON_ID));
    }

    @Test
    void elPartidoTerminadoTieneSuResultadoFinalElDelPrimerTiempoYSuGanador() {
        LeagueSnapshot premier = snapshot(League.PREMIER);

        apply(premier);
        List<Match> saved = matchRepository.findAllByExternalIds(List.of(finishedMatchId(League.PREMIER)));

        assertThat(saved).singleElement().satisfies(match -> {
            assertThat(match.fullTime()).isEqualTo(new Score(2, 1));
            assertThat(match.halfTime()).isEqualTo(new Score(1, 0));
            assertThat(match.winner()).isEqualTo(MatchWinner.HOME_TEAM);
        });
    }

    @Test
    void unPartidoProgramadoQueLaFuenteInformaTerminadoEsLaMismaFilaConSuResultado() {
        String matchId = timedMatchId(League.PREMIER);
        apply(premierWith(match(matchId, PREMIER_SEASON_ID, MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID)));
        Long rowId = matchRepository.findAllByExternalIds(List.of(matchId)).getFirst().id();

        apply(premierWith(finishedMatch(matchId, PREMIER_SEASON_ID, LIVERPOOL_ID, CHELSEA_ID)));
        Match updated = matchRepository.findAllByExternalIds(List.of(matchId)).getFirst();

        assertThat(updated.id()).isEqualTo(rowId);
        assertThat(updated.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(updated.fullTime()).isEqualTo(new Score(2, 1));
        assertThat(updated.winner()).isEqualTo(MatchWinner.HOME_TEAM);
        assertThat(matchDAO.count()).isEqualTo(1);
    }

    @Test
    void unPartidoPostergadoQueCambiaDeFechaEsLaMismaFilaConLaFechaNueva() {
        String matchId = timedMatchId(League.PREMIER);
        Instant newDate = TIMED_KICK_OFF.plus(Duration.ofDays(21));
        apply(premierWith(match(matchId, PREMIER_SEASON_ID, MatchStatus.POSTPONED, LIVERPOOL_ID, CHELSEA_ID)));
        Long rowId = matchRepository.findAllByExternalIds(List.of(matchId)).getFirst().id();

        apply(premierWith(new MatchSnapshot(matchId, PREMIER_SEASON_ID, newDate, CURRENT_MATCHDAY + 1,
                MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID, null, null, null)));
        Match updated = matchRepository.findAllByExternalIds(List.of(matchId)).getFirst();

        assertThat(updated.id()).isEqualTo(rowId);
        assertThat(updated.utcDate()).isEqualTo(newDate);
        assertThat(updated.status()).isEqualTo(MatchStatus.TIMED);
        assertThat(matchDAO.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = MatchStatus.class, names = {"POSTPONED", "SUSPENDED", "CANCELLED", "AWARDED"})
    void unPartidoQueNoSeJugoNormalmenteSeGuardaConSuEstado(MatchStatus status) {
        String matchId = timedMatchId(League.PREMIER);

        apply(premierWith(match(matchId, PREMIER_SEASON_ID, status, LIVERPOOL_ID, CHELSEA_ID)));
        List<Match> saved = matchRepository.findAllByExternalIds(List.of(matchId));

        assertThat(saved).singleElement().satisfies(match -> assertThat(match.status()).isEqualTo(status));
    }

    @Test
    void unPartidoConUnEquipoFueraDelCatalogoNoSeGuardaYLaLigaNoFalla() {
        MatchSnapshot withUnknownTeam = match("250299", PREMIER_SEASON_ID, MatchStatus.TIMED, LIVERPOOL_ID, "1076");
        LeagueSnapshot premier = premierWith(
                finishedMatch(finishedMatchId(League.PREMIER), PREMIER_SEASON_ID, LIVERPOOL_ID, CHELSEA_ID),
                withUnknownTeam);

        LeagueSyncResult result = apply(premier);

        assertThat(result.status()).isEqualTo(LeagueSyncStatus.SUCCEEDED);
        assertThat(result.skippedMatches()).containsExactly(
                new SkippedMatch("250299", LIVERPOOL_ID, "1076", TIMED_KICK_OFF, MatchSkipReason.UNKNOWN_TEAM));
        assertThat(matchRepository.findAllByExternalIds(List.of("250299"))).isEmpty();
        assertThat(matchDAO.count()).isEqualTo(1);
    }

    @Test
    void otraTemporadaEnCursoSeCreaConSusPartidosYLaAnteriorSeConservaConLosSuyos() {
        LeagueSnapshot current = snapshot(League.PREMIER);
        apply(current);
        Season next = new Season(null, "2602", League.PREMIER, LocalDate.of(2027, 8, 20), LocalDate.of(2028, 5, 28), 1);
        MatchSnapshot nextMatch = new MatchSnapshot("260201", "2602", Instant.parse("2027-08-21T14:00:00Z"), 1,
                MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID, null, null, null);
        LeagueSnapshot nextSeason = new LeagueSnapshot(League.PREMIER, next, List.of(liverpool(), chelsea()),
                List.of(nextMatch));

        apply(nextSeason);
        var previousSeason = seasonRepository.findByExternalId(PREMIER_SEASON_ID);
        List<Match> previousMatches = matchRepository.findAllByExternalIds(current.matchExternalIds());
        List<Match> nextMatches = matchRepository.findAllByExternalIds(List.of("260201"));

        assertThat(seasonDAO.count()).isEqualTo(2);
        assertThat(previousSeason).isPresent();
        assertThat(previousMatches).hasSize(2).allSatisfy(match ->
                assertThat(match.season().externalId()).isEqualTo(PREMIER_SEASON_ID));
        assertThat(nextMatches).singleElement().satisfies(match ->
                assertThat(match.season().externalId()).isEqualTo("2602"));
        assertThat(matchDAO.count()).isEqualTo(3);
    }

    /**
     * El partido nuevo tiene un {@code externalId} de 33 caracteres, que la columna
     * {@code VARCHAR(32)} rechaza: es el último paso de la escritura, así que antes ya se renombró el
     * equipo y se insertó el jugador nuevo, y todo eso tiene que revertirse (FR-034 y SC-008).
     */
    @Test
    void unaLigaQueFallaAlEscribirSeRevierteEnteraYConservaLoQueTenia() {
        apply(snapshot(League.PREMIER));
        long teamsBefore = teamDAO.count();
        long playersBefore = playerDAO.count();
        long seasonsBefore = seasonDAO.count();
        long matchesBefore = matchDAO.count();
        List<PlayerSnapshot> squadWithNewPlayer = new ArrayList<>(liverpool().squad());
        squadWithNewPlayer.add(player("64098", "Jugador Nuevo", Position.MIDFIELDER));
        TeamSnapshot renamedLiverpool = new TeamSnapshot(LIVERPOOL_ID, "Liverpool Football Club", LIVERPOOL_CREST,
                squadWithNewPlayer);
        MatchSnapshot tooLongId = match("9".repeat(33), PREMIER_SEASON_ID, MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID);
        LeagueSnapshot broken = snapshot(League.PREMIER, List.of(renamedLiverpool, chelsea()), List.of(tooLongId));

        Throwable failure = catchThrowable(() -> apply(broken));
        List<Team> savedLiverpool = teamRepository.findAllByExternalIds(List.of(LIVERPOOL_ID));
        var newPlayer = playerRepository.findByExternalId("64098");

        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(savedLiverpool).singleElement().satisfies(team -> assertThat(team.name()).isEqualTo(LIVERPOOL_NAME));
        assertThat(newPlayer).isEmpty();
        assertThat(teamDAO.count()).isEqualTo(teamsBefore);
        assertThat(playerDAO.count()).isEqualTo(playersBefore);
        assertThat(seasonDAO.count()).isEqualTo(seasonsBefore);
        assertThat(matchDAO.count()).isEqualTo(matchesBefore);
    }

    @Test
    void unJugadorQueLlegaEnElPlantelDeUnEquipoDeOtraLigaEsLaMismaFilaConElEquipoYLaLigaNuevos() {
        apply(snapshot(League.PREMIER));
        Long rowId = playerRepository.findByExternalId(CHIESA_ID).orElseThrow().id();
        long playersBefore = playerDAO.count();

        apply(snapshot(League.BUNDESLIGA, team(BAYERN_ID, BAYERN_NAME, chiesa())));
        Player transferred = playerRepository.findByExternalId(CHIESA_ID).orElseThrow();

        assertThat(transferred.id()).isEqualTo(rowId);
        assertThat(transferred.team().externalId()).isEqualTo(BAYERN_ID);
        assertThat(transferred.team().name()).isEqualTo(BAYERN_NAME);
        assertThat(transferred.league()).isEqualTo(League.BUNDESLIGA);
        assertThat(playerDAO.count()).isEqualTo(playersBefore);
    }

    @Test
    void unJugadorInactivoQueVuelveALlegarQuedaActivoEnElEquipoInformadoYFiguraEntreLosReactivados() {
        apply(snapshot(League.PREMIER));
        playerRepository.save(playerRepository.findByExternalId(ALISSON_ID).orElseThrow().deactivate());

        LeagueSyncResult result = apply(snapshot(League.PREMIER, team(CHELSEA_ID, CHELSEA_NAME, alisson())));
        Player reactivated = playerRepository.findByExternalId(ALISSON_ID).orElseThrow();

        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.team().externalId()).isEqualTo(CHELSEA_ID);
        assertThat(result.reactivatedPlayers()).extracting(Player::externalId).containsExactly(ALISSON_ID);
    }

    @Test
    void losDatosCorregidosDelJugadorYElNombreOficialYElEscudoDelEquipoSeActualizan() {
        apply(snapshot(League.PREMIER));
        String newCrest = "https://crests.football-data.org/64.svg";
        PlayerSnapshot corrected = new PlayerSnapshot(TSIMIKAS_ID, "Konstantinos Tsimikas", Position.MIDFIELDER,
                LocalDate.of(1996, 5, 13), "Grecia");
        TeamSnapshot renamed = new TeamSnapshot(LIVERPOOL_ID, "Liverpool Football Club", newCrest, List.of(corrected));

        apply(snapshot(League.PREMIER, renamed));
        Player player = playerRepository.findByExternalId(TSIMIKAS_ID).orElseThrow();
        List<Team> savedLiverpool = teamRepository.findAllByExternalIds(List.of(LIVERPOOL_ID));

        assertThat(player.name()).isEqualTo("Konstantinos Tsimikas");
        assertThat(player.position()).isEqualTo(Position.MIDFIELDER);
        assertThat(player.dateOfBirth()).isEqualTo(LocalDate.of(1996, 5, 13));
        assertThat(player.nationality()).isEqualTo("Grecia");
        assertThat(savedLiverpool).singleElement().satisfies(liverpool -> {
            assertThat(liverpool.name()).isEqualTo("Liverpool Football Club");
            assertThat(liverpool.crest()).isEqualTo(newCrest);
        });
    }

    @Test
    void unJugadorGuardadoQueLlegaSinPosicionConservaLaSuya() {
        apply(snapshot(League.PREMIER));

        apply(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, player(ALISSON_ID, "Alisson Becker", null))));
        Player alisson = playerRepository.findByExternalId(ALISSON_ID).orElseThrow();

        assertThat(alisson.position()).isEqualTo(Position.GOALKEEPER);
        assertThat(alisson.active()).isTrue();
    }

    @Test
    void unEquipoQueNoLlegaPorqueDescendioQuedaIgualYConservaSuLiga() {
        apply(snapshot(League.PREMIER));

        apply(snapshot(League.PREMIER, liverpool()));
        List<Team> savedChelsea = teamRepository.findAllByExternalIds(List.of(CHELSEA_ID));

        assertThat(savedChelsea).singleElement().satisfies(team -> {
            assertThat(team.name()).isEqualTo(CHELSEA_NAME);
            assertThat(team.crest()).isEqualTo(CHELSEA_CREST);
            assertThat(team.league()).isEqualTo(League.PREMIER);
        });
        assertThat(teamDAO.count()).isEqualTo(PREMIER_TEAMS);
    }

    @Test
    void conUnaCompletaConLasCincoLigasEnExitoSeInactivaAlJugadorQueNoLlegoYConservaSuEquipo() {
        apply(snapshot(League.PREMIER));
        SyncRun run = fullRunWithout(ALISSON_ID, null);

        List<Player> inactivated = writeService.deactivateMissing(run);
        var alisson = playerRepository.findByExternalId(ALISSON_ID);

        assertThat(inactivated).extracting(Player::externalId).containsExactly(ALISSON_ID);
        assertThat(alisson).hasValueSatisfying(player -> {
            assertThat(player.active()).isFalse();
            assertThat(player.team().externalId()).isEqualTo(LIVERPOOL_ID);
        });
    }

    @Test
    void conUnaLigaFallidaNoSeInactivaANadie() {
        apply(snapshot(League.PREMIER));
        SyncRun run = fullRunWithout(ALISSON_ID, League.LIGUE_1);

        List<Player> inactivated = writeService.deactivateMissing(run);
        var alisson = playerRepository.findByExternalId(ALISSON_ID);

        assertThat(inactivated).isEmpty();
        assertThat(alisson).hasValueSatisfying(player -> assertThat(player.active()).isTrue());
    }

    @Test
    void lasCantidadesNuncaBajanCuandoLaSegundaTraeMenosJugadoresYEquipos() {
        apply(snapshot(League.PREMIER));
        long teamsBefore = teamDAO.count();
        long playersBefore = playerDAO.count();
        long seasonsBefore = seasonDAO.count();
        long matchesBefore = matchDAO.count();

        apply(snapshot(League.PREMIER, team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson())));

        assertThat(teamDAO.count()).isGreaterThanOrEqualTo(teamsBefore);
        assertThat(playerDAO.count()).isGreaterThanOrEqualTo(playersBefore);
        assertThat(seasonDAO.count()).isGreaterThanOrEqualTo(seasonsBefore);
        assertThat(matchDAO.count()).isGreaterThanOrEqualTo(matchesBefore);
    }

    /**
     * Una completa que descargó las cinco ligas sin el jugador y las escribió todas, salvo la que
     * falla, si se indica una.
     */
    private SyncRun fullRunWithout(String playerExternalId, League failedLeague) {
        List<LeagueSnapshot> snapshots = allSnapshots().stream()
                .filter(snapshot -> snapshot.league() != failedLeague)
                .map(snapshot -> withoutPlayer(snapshot, playerExternalId))
                .toList();
        SyncRun run = SyncRun.full(SyncOrigin.WEEKLY, Instant.parse("2026-10-12T07:00:00Z"));
        run.registerSnapshots(snapshots);
        snapshots.forEach(snapshot -> run.recordSuccess(apply(snapshot)));
        if (failedLeague != null) {
            run.recordFailure(failedLeague, "La fuente no respondió a tiempo.");
        }
        return run;
    }

    private LeagueSyncResult apply(LeagueSnapshot snapshot) {
        return writeService.applyLeague(snapshot, SquadAssignment.of(List.of(snapshot)).resolve(List.of()));
    }

    /**
     * La Premier de las fixtures, con sus dos equipos y solo los partidos recibidos.
     */
    private static LeagueSnapshot premierWith(MatchSnapshot... matches) {
        return snapshot(League.PREMIER, List.of(liverpool(), chelsea()), List.of(matches));
    }
}
