package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;

/**
 * La semilla contra la base, con una H2 propia: al arrancar quedan la A activa en su versión 1 y la
 * B en su versión 1 (US4.1), y correrla otra vez no duplica nada (US4.2).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:strategy-seeder-it;DB_CLOSE_DELAY=-1")
class StrategySeederIT {
    private static final int STARTING_STRATEGIES = 2;

    @Autowired
    private StrategySeeder seeder;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private StrategySQLDAO strategyDAO;

    private final ApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void alArrancarQuedanLaAActivaYLaBEnSuVersionUno() {
        StrategyCatalog catalog = strategyRepository.findCatalog();

        assertThat(catalog.strategies()).hasSize(STARTING_STRATEGIES);
        assertThat(catalog.activeVersion().name()).isEqualTo(StartingStrategies.MATCH_PERFORMANCE_NAME);
        assertThat(catalog.activeVersion().version()).isEqualTo(1);
        assertThat(catalog.activeVersion().defaultWeights()).isEqualTo(StartingStrategies.MATCH_PERFORMANCE.defaultWeights());
        var balanced = catalog.findByName(StartingStrategies.BALANCED_BY_POSITION_NAME);
        assertThat(balanced.versions()).hasSize(1);
        assertThat(catalog.isActive(balanced.id())).isFalse();
        assertThat(balanced.latest().weightsFor(Position.GOALKEEPER))
                .isEqualTo(StartingStrategies.BALANCED_BY_POSITION.positionWeights().get(Position.GOALKEEPER));
    }

    @Test
    void correrLaSemillaOtraVezNoDuplicaNiCambiaLaActiva() {
        Long activeVersionId = strategyRepository.findCatalog().activeVersion().id();

        seeder.run(args);
        seeder.run(args);

        assertThat(strategyDAO.count()).isEqualTo(STARTING_STRATEGIES);
        assertThat(strategyRepository.findCatalog().activeVersion().id()).isEqualTo(activeVersionId);
    }
}
