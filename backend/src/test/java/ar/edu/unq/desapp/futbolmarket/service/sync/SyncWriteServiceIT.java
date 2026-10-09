package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.EntityCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
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
}
