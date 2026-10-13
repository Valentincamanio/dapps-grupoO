package ar.edu.unq.desapp.futbolmarket.persistence.repository.season;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sync-it;DB_CLOSE_DELAY=-1")
class SeasonRepositoryIT {
    private static final String SEASON_ID = "2502";
    private static final LocalDate START = LocalDate.of(2026, 8, 21);
    private static final LocalDate END = LocalDate.of(2027, 5, 30);
    private static final String EXTERNAL_ID_INDEX = "ux_seasons_external_id";

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @BeforeEach
    void cleanDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }

    @Test
    void guardaYRecuperaPorIdentificadorExternoLaLigaLasFechasYLaJornada() {
        Season saved = seasonRepository.save(premierSeason(6));

        Optional<Season> found = seasonRepository.findByExternalId(SEASON_ID);

        assertThat(saved.id()).isNotNull();
        assertThat(found).contains(new Season(saved.id(), SEASON_ID, League.PREMIER, START, END, 6));
    }

    @Test
    void unaTemporadaSinJornadaActualVuelveSinJornada() {
        seasonRepository.save(premierSeason(null));

        Optional<Season> found = seasonRepository.findByExternalId(SEASON_ID);

        assertThat(found).hasValueSatisfying(season -> assertThat(season.currentMatchday()).isNull());
    }

    @Test
    void findByExternalIdDeUnaTemporadaQueNoEstaGuardadaDevuelveVacio() {
        seasonRepository.save(premierSeason(6));

        Optional<Season> found = seasonRepository.findByExternalId("2503");

        assertThat(found).isEmpty();
    }

    @Test
    void saveActualizaUnaTemporadaExistenteSinCrearOtra() {
        Season saved = seasonRepository.save(premierSeason(6));

        Season updated = seasonRepository.save(saved.updateFrom(premierSeason(7)));

        assertThat(updated).isEqualTo(new Season(saved.id(), SEASON_ID, League.PREMIER, START, END, 7));
        assertThat(seasonDAO.count()).isEqualTo(1);
    }

    @Test
    void unIdentificadorExternoRepetidoViolaElIndiceUnico() {
        seasonRepository.save(premierSeason(6));
        var repeated = premierSeason(7);

        assertThatThrownBy(() -> seasonRepository.save(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(EXTERNAL_ID_INDEX);
    }

    private static Season premierSeason(Integer currentMatchday) {
        return new Season(null, SEASON_ID, League.PREMIER, START, END, currentMatchday);
    }
}
