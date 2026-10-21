package ar.edu.unq.desapp.futbolmarket.persistence.repository.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.sync.LastFullSyncSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class LastFullSyncRepositoryIT {
    private static final Instant FIRST_FINISH = Instant.parse("2026-10-05T07:12:45Z");
    private static final Instant SECOND_FINISH = Instant.parse("2026-10-12T07:47:30Z");

    @Autowired
    private LastFullSyncRepository lastFullSyncRepository;

    @Autowired
    private PlayerMatchStatsSQLDAO statsDAO;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @Autowired
    private LastFullSyncSQLDAO lastFullSyncDAO;

    @BeforeEach
    void cleanDatabaseBefore() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void sinFilasNuncaHuboUnaCompletaExitosaDeNingunaFuente() {
        Optional<LastFullSync> footballData = lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA);
        Optional<LastFullSync> whoScored = lastFullSyncRepository.find(SyncSource.WHOSCORED);

        assertThat(footballData).isEmpty();
        assertThat(whoScored).isEmpty();
    }

    @Test
    void guardaYLeeLaUltimaCompletaDeUnaFuente() {
        lastFullSyncRepository.save(new LastFullSync(SyncSource.WHOSCORED, FIRST_FINISH));

        Optional<LastFullSync> whoScored = lastFullSyncRepository.find(SyncSource.WHOSCORED);
        Optional<LastFullSync> footballData = lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA);

        assertThat(whoScored).contains(new LastFullSync(SyncSource.WHOSCORED, FIRST_FINISH));
        assertThat(footballData).isEmpty();
    }

    @Test
    void guardarDosVecesLaMismaFuenteDejaUnaFilaConElUltimoMomento() {
        lastFullSyncRepository.save(new LastFullSync(SyncSource.FOOTBALL_DATA, FIRST_FINISH));

        lastFullSyncRepository.save(new LastFullSync(SyncSource.FOOTBALL_DATA, SECOND_FINISH));

        assertThat(lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA))
                .contains(new LastFullSync(SyncSource.FOOTBALL_DATA, SECOND_FINISH));
        assertThat(lastFullSyncDAO.count()).isEqualTo(1);
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        lastFullSyncDAO.deleteAll();
    }
}
