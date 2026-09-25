package ar.edu.unq.desapp.futbolmarket.catalog.persistence;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.TeamSQLDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

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
        Team team = teamRepository.findOrCreate("Arsenal", League.PREMIER);
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
        Team arsenal = teamRepository.findOrCreate("Arsenal", League.PREMIER);
        Team chelsea = teamRepository.findOrCreate("Chelsea", League.PREMIER);
        Team bayern = teamRepository.findOrCreate("Bayern Munich", League.BUNDESLIGA);
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
        Team arsenal = teamRepository.findOrCreate("Arsenal", League.PREMIER);
        playerRepository.save(new Player("arsenal-01", "Bukayo Saka", Position.FORWARD, arsenal));

        var page = playerRepository.findPage(0, 10, new PlayerFilter(League.PREMIER, "Arsenal", Position.GOALKEEPER));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }
}
