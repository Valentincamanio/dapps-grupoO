package ar.edu.unq.desapp.futbolmarket.config;

import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.sql.interfaces.TeamSQLDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlayerCatalogDataSeederIT {
    private static final long EXPECTED_PLAYERS = 50;
    private static final long EXPECTED_TEAMS = 15;

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
    void cargaElDatasetSinDuplicarJugadoresNiEquipos() throws Exception {
        var seeder = new PlayerCatalogDataSeeder(playerRepository, teamRepository);

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(playerDAO.count()).isEqualTo(EXPECTED_PLAYERS);
        assertThat(teamDAO.count()).isEqualTo(EXPECTED_TEAMS);
        assertThat(playerRepository.findByExternalId("premier-01"))
                .hasValueSatisfying(player -> assertThat(player.team().name()).isEqualTo("Liverpool"));
    }
}
