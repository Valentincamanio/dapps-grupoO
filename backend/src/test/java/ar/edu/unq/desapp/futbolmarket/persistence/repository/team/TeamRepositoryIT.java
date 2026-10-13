package ar.edu.unq.desapp.futbolmarket.persistence.repository.team;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class TeamRepositoryIT {
    private static final String LIVERPOOL_CREST = "https://crests.football-data.org/64.png";
    private static final String CHELSEA_CREST = "https://crests.football-data.org/61.png";
    private static final String EXTERNAL_ID_INDEX = "ux_teams_external_id";

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
    void saveAllYFindAllByExternalIdsGuardanYRecuperanLosDatosDelEquipo() {
        var liverpool = new Team("64", "Liverpool FC", LIVERPOOL_CREST, League.PREMIER);
        var chelsea = new Team("61", "Chelsea FC", CHELSEA_CREST, League.PREMIER);

        List<Team> saved = teamRepository.saveAll(List.of(liverpool, chelsea));
        List<Team> found = teamRepository.findAllByExternalIds(List.of("64", "61"));

        assertThat(saved).allSatisfy(team -> assertThat(team.id()).isNotNull());
        assertThat(found).containsExactlyInAnyOrderElementsOf(saved);
        assertThat(found)
                .extracting(Team::externalId, Team::name, Team::crest, Team::league)
                .containsExactlyInAnyOrder(
                        tuple("64", "Liverpool FC", LIVERPOOL_CREST, League.PREMIER),
                        tuple("61", "Chelsea FC", CHELSEA_CREST, League.PREMIER));
    }

    @Test
    void saveAllActualizaAUnEquipoExistenteSinCrearOtro() {
        Team saved = teamRepository.save(new Team("64", "Liverpool", null, League.PREMIER));
        var snapshot = new TeamSnapshot("64", "Liverpool FC", LIVERPOOL_CREST, List.of());

        List<Team> updated = teamRepository.saveAll(List.of(saved.updateFrom(snapshot, League.PREMIER)));

        assertThat(updated).containsExactly(new Team(saved.id(), "64", "Liverpool FC", LIVERPOOL_CREST, League.PREMIER));
        assertThat(teamDAO.count()).isEqualTo(1);
    }

    @Test
    void unNombreConDieresisSeGuardaTalCual() {
        teamRepository.save(new Team("5", "FC Bayern München", null, League.BUNDESLIGA));

        List<Team> found = teamRepository.findAllByExternalIds(List.of("5"));

        assertThat(found).extracting(Team::name).containsExactly("FC Bayern München");
    }

    @Test
    void unIdentificadorExternoRepetidoViolaElIndiceUnico() {
        teamRepository.save(new Team("64", "Liverpool FC", LIVERPOOL_CREST, League.PREMIER));
        var repeated = new Team("64", "Liverpool", null, League.PREMIER);

        assertThatThrownBy(() -> teamRepository.save(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(EXTERNAL_ID_INDEX);
    }

    @Test
    void findAllByExternalIdsConUnaColeccionVaciaDevuelveUnaListaVacia() {
        teamRepository.save(new Team("64", "Liverpool FC", LIVERPOOL_CREST, League.PREMIER));

        List<Team> found = teamRepository.findAllByExternalIds(List.of());

        assertThat(found).isEmpty();
    }
}
