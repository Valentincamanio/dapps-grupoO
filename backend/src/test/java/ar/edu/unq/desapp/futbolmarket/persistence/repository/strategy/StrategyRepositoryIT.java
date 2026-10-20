package ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy.StrategySQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * El catálogo de estrategias contra la base: versiones inmutables y una única marca activa.
 *
 * <p>Usa una H2 propia y la vacía antes de cada test, así no depende de lo que haya sembrado
 * {@code StrategySeeder} al arrancar.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:strategy-it;DB_CLOSE_DELAY=-1")
class StrategyRepositoryIT {
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));
    private static final WeightSet GOALS_AND_RATING = new WeightSet(Map.of(
            ValuationMetric.GOALS, new BigDecimal("0.60"),
            ValuationMetric.RATING, new BigDecimal("0.40")));
    private static final WeightSet TACKLES_AND_CLEAN_SHEETS = new WeightSet(Map.of(
            ValuationMetric.TACKLES, new BigDecimal("0.75"),
            ValuationMetric.CLEAN_SHEETS, new BigDecimal("0.25")));

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private StrategySQLDAO strategyDAO;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void unaBaseVaciaDevuelveUnCatalogoSinEstrategias() {
        StrategyCatalog catalog = strategyRepository.findCatalog();

        assertThat(catalog.strategies()).isEmpty();
    }

    @Test
    void idaYVueltaDeUnaEstrategiaConPesosPorDefectoYPorPosicion() {
        StrategyCatalog saved = strategyRepository.save(StrategyCatalog.empty().create("Ofensiva",
                new BigDecimal("5"), new BigDecimal("1.50"), GOALS_AND_RATING,
                Map.of(Position.DEFENDER, TACKLES_AND_CLEAN_SHEETS)));

        StrategyCatalog read = strategyRepository.findCatalog();

        StrategyVersion version = read.findByName("Ofensiva").latest();
        assertThat(read.strategies()).hasSize(1);
        assertThat(version.id()).isNotNull();
        assertThat(version.strategyId()).isEqualTo(version.id());
        assertThat(version.version()).isEqualTo(1);
        assertThat(version.scaleFactor()).isEqualTo(new BigDecimal("5.00"));
        assertThat(version.minimumValue()).isEqualTo(new BigDecimal("1.50"));
        assertThat(version.defaultWeights()).isEqualTo(GOALS_AND_RATING);
        assertThat(version.weightsFor(Position.DEFENDER)).isEqualTo(TACKLES_AND_CLEAN_SHEETS);
        assertThat(version.weightsFor(Position.FORWARD)).isEqualTo(GOALS_AND_RATING);
        assertThat(version.createdAt()).isNotNull();
        assertThat(saved.findByName("Ofensiva").id()).isEqualTo(version.id());
    }

    @Test
    void laPrimeraEstrategiaQuedaComoLaActiva() {
        strategyRepository.save(StrategyCatalog.empty().create("Ofensiva", null, null, ALL_GOALS, Map.of()));

        StrategyCatalog read = strategyRepository.findCatalog();

        assertThat(read.activeVersion().name()).isEqualTo("Ofensiva");
        assertThat(strategyDAO.findAll()).filteredOn(StrategySQL::isActive).hasSize(1);
    }

    @Test
    void unaVersionNuevaConservaLaAnteriorIntacta() {
        StrategyCatalog catalog = strategyRepository.save(
                StrategyCatalog.empty().create("Ofensiva", null, null, ALL_GOALS, Map.of()));
        Long strategyId = catalog.findByName("Ofensiva").id();
        StrategyVersion first = strategyRepository.findCatalog().find(strategyId).latest();

        strategyRepository.save(catalog.newVersion(strategyId, new BigDecimal("7"), null, GOALS_AND_RATING,
                Map.of(Position.DEFENDER, TACKLES_AND_CLEAN_SHEETS)));

        var strategy = strategyRepository.findCatalog().find(strategyId);
        assertThat(strategy.versions()).hasSize(2);
        assertThat(strategy.versions().getFirst()).isEqualTo(first);
        StrategyVersion second = strategy.latest();
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.strategyId()).isEqualTo(strategyId);
        assertThat(second.scaleFactor()).isEqualTo(new BigDecimal("7.00"));
        assertThat(second.minimumValue()).isEqualTo(first.minimumValue());
        assertThat(second.defaultWeights()).isEqualTo(GOALS_AND_RATING);
    }

    @Test
    void activarOtraDejaLaMarcaEnExactamenteUnaFila() {
        StrategyCatalog catalog = withTwoStrategies();
        Long defensiveId = catalog.findByName("Defensiva").id();

        strategyRepository.save(catalog.activate(defensiveId));

        StrategyCatalog read = strategyRepository.findCatalog();
        assertThat(read.isActive(defensiveId)).isTrue();
        assertThat(strategyDAO.findAll())
                .filteredOn(StrategySQL::isActive)
                .extracting(StrategySQL::getName)
                .containsExactly("Defensiva");
    }

    @Test
    void editarLaActivaDejaActivaLaVersionNuevaYDesactivaLaAnterior() {
        StrategyCatalog catalog = withTwoStrategies();
        Long offensiveId = catalog.findByName("Ofensiva").id();

        strategyRepository.save(catalog.newVersion(offensiveId, null, null, GOALS_AND_RATING, Map.of()));

        StrategyCatalog read = strategyRepository.findCatalog();
        assertThat(read.activeVersion().version()).isEqualTo(2);
        assertThat(read.activeVersion().strategyId()).isEqualTo(offensiveId);
        assertThat(strategyDAO.findAll())
                .filteredOn(StrategySQL::isActive)
                .extracting(StrategySQL::getName, StrategySQL::getVersionNumber)
                .containsExactly(tuple("Ofensiva", 2));
    }

    @Test
    void editarUnaInactivaNoMueveLaMarcaActiva() {
        StrategyCatalog catalog = withTwoStrategies();
        Long defensiveId = catalog.findByName("Defensiva").id();
        Long activeVersionId = catalog.activeVersion().id();

        strategyRepository.save(catalog.newVersion(defensiveId, null, null, ALL_GOALS, Map.of()));

        StrategyCatalog read = strategyRepository.findCatalog();
        assertThat(read.activeVersion().id()).isEqualTo(activeVersionId);
        assertThat(read.find(defensiveId).versions()).hasSize(2);
    }

    @Test
    void elIndiceUnicoRechazaUnNombreYUnaVersionRepetidos() {
        strategyRepository.save(StrategyCatalog.empty().create("Ofensiva", null, null, ALL_GOALS, Map.of()));
        StrategySQL duplicate = new StrategySQL(null, "Ofensiva", 1, BigDecimal.TEN, BigDecimal.TEN, false);

        assertThatThrownBy(() -> strategyDAO.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private StrategyCatalog withTwoStrategies() {
        StrategyCatalog catalog = strategyRepository.save(
                StrategyCatalog.empty().create("Ofensiva", null, null, ALL_GOALS, Map.of()));
        return strategyRepository.save(catalog.create("Defensiva", null, null, TACKLES_AND_CLEAN_SHEETS, Map.of()));
    }

    /**
     * Borra primero las versiones más altas: las siguientes a la 1 apuntan a su raíz.
     */
    private void cleanDatabase() {
        strategyDAO.deleteAll(strategyDAO.findAll().stream()
                .sorted(Comparator.comparingInt(StrategySQL::getVersionNumber).reversed())
                .toList());
    }
}
