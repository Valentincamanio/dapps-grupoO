package ar.edu.unq.desapp.futbolmarket.persistence.repository.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Fija el armado de los IT de la base {@code stats-it}: una H2 propia, que se limpia antes y
 * después de cada test para no dejar filas que bloqueen la limpieza de otra clase.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class TeamCrossRepositoryIT {
    private static final String WHOSCORED_ID_INDEX = "ux_teams_whoscored_id";

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

    @BeforeEach
    void cleanDatabaseBefore() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void elIdentificadorDeWhoScoredSeGuardaYSeLee() {
        teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));

        List<Team> found = teamRepository.findAllByExternalIds(List.of("62"));

        assertThat(found).singleElement().satisfies(team -> {
            assertThat(team.whoscoredId()).isEqualTo("31");
            assertThat(team.isCrossed()).isTrue();
        });
    }

    @Test
    void variosEquiposSinIdentificadorDeWhoScoredConviven() {
        List<Team> uncrossed = List.of(
                new Team("62", "Everton FC", null, League.PREMIER),
                new Team("66", "Manchester United FC", null, League.PREMIER),
                new Team("73", "Tottenham Hotspur FC", null, League.PREMIER));

        List<Team> saved = teamRepository.saveAll(uncrossed);

        assertThat(saved).extracting(Team::whoscoredId).containsOnlyNulls();
        assertThat(teamDAO.count()).isEqualTo(3);
    }

    @Test
    void dosEquiposConElMismoIdentificadorDeWhoScoredViolanElIndiceUnico() {
        teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));
        Team repeated = new Team(null, "66", "Manchester United FC", null, League.PREMIER, "31");

        assertThatThrownBy(() -> teamRepository.save(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(WHOSCORED_ID_INDEX);
    }

    @Test
    void findAllCrossedTraeSoloLosEquiposCruzados() {
        teamRepository.saveAll(List.of(
                new Team(null, "62", "Everton FC", null, League.PREMIER, "31"),
                new Team("66", "Manchester United FC", null, League.PREMIER),
                new Team(null, "73", "Tottenham Hotspur FC", null, League.PREMIER, "30")));

        List<Team> crossed = teamRepository.findAllCrossed();

        assertThat(crossed)
                .extracting(Team::externalId, Team::whoscoredId)
                .containsExactlyInAnyOrder(tuple("62", "31"), tuple("73", "30"));
    }

    private void cleanDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
