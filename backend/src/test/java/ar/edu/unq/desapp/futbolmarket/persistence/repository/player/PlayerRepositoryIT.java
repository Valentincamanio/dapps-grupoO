package ar.edu.unq.desapp.futbolmarket.persistence.repository.player;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class PlayerRepositoryIT {
    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @BeforeEach
    void cleanDatabase() {
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }

    @Test
    void persisteMapeaYPaginaJugadoresOrdenadosPorId() {
        Team team = teamRepository.save(new Team("57", "Arsenal", null, League.PREMIER));
        Player firstPlayer = playerRepository.save(new Player("arsenal-01", "Bukayo Saka", Position.FORWARD, team));
        Player secondPlayer = playerRepository.save(new Player("arsenal-02", "William Saliba", Position.DEFENDER, team));

        var firstPage = playerRepository.findPage(0, 1);
        var secondPage = playerRepository.findPage(1, 1);

        assertThat(firstPage.content()).containsExactly(firstPlayer);
        assertThat(secondPage.content()).containsExactly(secondPlayer);
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content().getFirst().team()).isEqualTo(team);
        assertThat(firstPage.content().getFirst().league()).isEqualTo(League.PREMIER);
    }

    @Test
    void aplicaFiltrosIndividualesYCombinadosConOrdenEstable() {
        Team arsenal = teamRepository.save(new Team("57", "Arsenal", null, League.PREMIER));
        Team chelsea = teamRepository.save(new Team("61", "Chelsea", null, League.PREMIER));
        Team bayern = teamRepository.save(new Team("5", "Bayern Munich", null, League.BUNDESLIGA));
        Player arsenalForward = playerRepository.save(new Player("arsenal-01", "Bukayo Saka", Position.FORWARD, arsenal));
        playerRepository.save(new Player("arsenal-02", "William Saliba", Position.DEFENDER, arsenal));
        playerRepository.save(new Player("chelsea-01", "Cole Palmer", Position.FORWARD, chelsea));
        playerRepository.save(new Player("bayern-01", "Jamal Musiala", Position.MIDFIELDER, bayern));

        var leaguePage = playerRepository.findPage(0, 10, new PlayerFilter(League.PREMIER, null, null));
        var teamPage = playerRepository.findPage(0, 10, new PlayerFilter(null, "  Arsenal  ", null));
        var combinedPage = playerRepository.findPage(0, 10, new PlayerFilter(League.PREMIER, "Arsenal", Position.FORWARD));

        assertThat(leaguePage.content()).allMatch(player -> player.league() == League.PREMIER);
        assertThat(teamPage.content()).allMatch(player -> player.team().name().equals("Arsenal"));
        assertThat(combinedPage.content()).containsExactly(arsenalForward);
    }

    @Test
    void devuelvePaginaVaciaParaFiltrosValidosSinCoincidencias() {
        Team arsenal = teamRepository.save(new Team("57", "Arsenal", null, League.PREMIER));
        playerRepository.save(new Player("arsenal-01", "Bukayo Saka", Position.FORWARD, arsenal));

        var page = playerRepository.findPage(0, 10, new PlayerFilter(League.PREMIER, "Arsenal", Position.GOALKEEPER));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void saveAllDevuelveLosJugadoresConIdYConLosCamposNuevos() {
        Team liverpool = teamRepository.save(liverpool());

        List<Player> saved = playerRepository.saveAll(List.of(alisson(liverpool), chiesa(liverpool, false)));

        assertThat(saved).allSatisfy(player -> assertThat(player.id()).isNotNull());
        assertThat(saved)
                .extracting(Player::externalId, Player::dateOfBirth, Player::nationality, Player::active)
                .containsExactly(
                        tuple("1795", LocalDate.of(1992, 10, 2), "Brazil", true),
                        tuple("1780", LocalDate.of(1997, 10, 25), "Italy", false));
    }

    @Test
    void saveAllActualizaAUnJugadorExistenteEnLaMismaFila() {
        Team juventus = teamRepository.save(new Team("109", "Juventus FC", null, League.SERIE_A));
        Team liverpool = teamRepository.save(liverpool());
        Player saved = playerRepository.save(chiesa(juventus, true));
        var snapshot = new PlayerSnapshot("1780", "Federico Chiesa", Position.FORWARD, LocalDate.of(1997, 10, 25), "Italy");

        List<Player> updated = playerRepository.saveAll(List.of(saved.updateFrom(snapshot, liverpool)));
        Optional<Player> reloaded = playerRepository.findById(saved.id());

        assertThat(updated).extracting(Player::id).containsExactly(saved.id());
        assertThat(reloaded).hasValueSatisfying(player -> assertThat(player.team()).isEqualTo(liverpool));
        assertThat(playerDAO.count()).isEqualTo(1);
    }

    @Test
    void findAllByExternalIdsDevuelveSoloLosPedidosConSuEquipoAunqueEstenInactivos() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.saveAll(List.of(alisson(liverpool), tsimikas(liverpool), chiesa(liverpool, false)));

        List<Player> found = playerRepository.findAllByExternalIds(List.of("1795", "1780"));

        assertThat(found).extracting(Player::externalId).containsExactlyInAnyOrder("1795", "1780");
        assertThat(found).extracting(player -> player.team().name()).containsOnly("Liverpool FC");
    }

    @Test
    void findAllByExternalIdsConUnaColeccionVaciaDevuelveUnaListaVacia() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.save(alisson(liverpool));

        List<Player> found = playerRepository.findAllByExternalIds(List.of());

        assertThat(found).isEmpty();
    }

    @Test
    void findAllActiveExcluyeALosInactivos() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.saveAll(List.of(alisson(liverpool), chiesa(liverpool, false)));

        List<Player> active = playerRepository.findAllActive();

        assertThat(active).extracting(Player::externalId).containsExactly("1795");
    }

    @Test
    void hasPlayersEsFalsoConLaTablaVacia() {
        boolean hasPlayers = playerRepository.hasPlayers();

        assertThat(hasPlayers).isFalse();
    }

    @Test
    void hasPlayersEsVerdaderoDespuesDeGuardarUnJugador() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.save(alisson(liverpool));

        boolean hasPlayers = playerRepository.hasPlayers();

        assertThat(hasPlayers).isTrue();
    }

    @Test
    void findByIdDevuelveAUnJugadorInactivo() {
        Team liverpool = teamRepository.save(liverpool());
        Player inactive = playerRepository.save(chiesa(liverpool, false));

        Optional<Player> found = playerRepository.findById(inactive.id());

        assertThat(found).contains(inactive);
    }

    @Test
    void unJugadorInactivoNoApareceEnElListadoNiFiltrandoPorSuUltimoEquipoSuLigaOSuPosicion() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.saveAll(List.of(alisson(liverpool), chiesa(liverpool, false)));

        var withoutFilters = playerRepository.findPage(0, 10);
        var byTeam = playerRepository.findPage(0, 10, new PlayerFilter(null, "Liverpool FC", null));
        var byLeague = playerRepository.findPage(0, 10, new PlayerFilter(League.PREMIER, null, null));
        var byPosition = playerRepository.findPage(0, 10, new PlayerFilter(null, null, Position.FORWARD));

        assertThat(List.of(withoutFilters, byTeam, byLeague)).allSatisfy(page ->
                assertThat(page.content()).extracting(Player::externalId).containsExactly("1795"));
        assertThat(byPosition.content()).isEmpty();
    }

    @Test
    void elTotalDeElementosCuentaSoloALosJugadoresActivos() {
        Team liverpool = teamRepository.save(liverpool());
        playerRepository.saveAll(List.of(alisson(liverpool), tsimikas(liverpool), chiesa(liverpool, false)));

        var withoutFilters = playerRepository.findPage(0, 10);
        var byTeam = playerRepository.findPage(0, 10, new PlayerFilter(null, "Liverpool FC", null));

        assertThat(withoutFilters.totalElements()).isEqualTo(2);
        assertThat(byTeam.totalElements()).isEqualTo(2);
    }

    private static Team liverpool() {
        return new Team("64", "Liverpool FC", "https://crests.football-data.org/64.png", League.PREMIER);
    }

    private static Player alisson(Team team) {
        return new Player(null, "1795", "Alisson Becker", Position.GOALKEEPER, team, LocalDate.of(1992, 10, 2), "Brazil", true);
    }

    private static Player tsimikas(Team team) {
        return new Player(null, "7383", "Kostas Tsimikas", Position.DEFENDER, team, LocalDate.of(1996, 5, 12), "Greece", true);
    }

    private static Player chiesa(Team team, boolean active) {
        return new Player(null, "1780", "Federico Chiesa", Position.FORWARD, team, LocalDate.of(1997, 10, 25), "Italy", active);
    }
}
