package ar.edu.unq.desapp.futbolmarket.persistence.repository.player;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class PlayerCrossRepositoryIT {
    private static final String WHOSCORED_ID_INDEX = "ux_players_whoscored_id";

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    private Team everton;
    private Team tottenham;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        everton = teamRepository.save(new Team("62", "Everton FC", null, League.PREMIER));
        tottenham = teamRepository.save(new Team("73", "Tottenham Hotspur FC", null, League.PREMIER));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void variosJugadoresSinIdentificadorDeWhoScoredConviven() {
        List<Player> uncrossed = List.of(
                new Player("3204", "Jordan Pickford", Position.GOALKEEPER, everton),
                new Player("3205", "James Tarkowski", Position.DEFENDER, everton),
                new Player("7801", "Guglielmo Vicario", Position.GOALKEEPER, tottenham));

        List<Player> saved = playerRepository.saveAll(uncrossed);

        assertThat(saved).extracting(Player::whoscoredId).containsOnlyNulls();
        assertThat(playerDAO.count()).isEqualTo(3);
    }

    @Test
    void dosJugadoresConElMismoIdentificadorDeWhoScoredViolanElIndiceUnico() {
        playerRepository.save(crossed("3204", "Jordan Pickford", everton, "93145"));
        Player repeated = crossed("7801", "Guglielmo Vicario", tottenham, "93145");

        assertThatThrownBy(() -> playerRepository.save(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(WHOSCORED_ID_INDEX);
    }

    @Test
    void findAllWithTeamTraeActivosEInactivosConElNombreDeSuEquipo() {
        playerRepository.save(crossed("3204", "Jordan Pickford", everton, "93145"));
        playerRepository.save(new Player("7801", "Guglielmo Vicario", Position.GOALKEEPER, tottenham).deactivate());

        List<Player> players = playerRepository.findAllWithTeam();

        assertThat(players)
                .extracting(Player::externalId, Player::active, player -> player.team().name(), Player::whoscoredId)
                .containsExactlyInAnyOrder(
                        tuple("3204", true, "Everton FC", "93145"),
                        tuple("7801", false, "Tottenham Hotspur FC", null));
    }

    private static Player crossed(String externalId, String name, Team team, String whoscoredId) {
        return new Player(null, externalId, name, Position.GOALKEEPER, team, LocalDate.of(1994, 3, 7), "England", true,
                whoscoredId);
    }

    private void cleanDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
