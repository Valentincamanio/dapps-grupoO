package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.DuplicateStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyCatalogTest {
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));
    private static final WeightSet ALL_TACKLES = new WeightSet(Map.of(ValuationMetric.TACKLES, BigDecimal.ONE));

    private static final long ACTIVE_ID = 1L;
    private static final long INACTIVE_ID = 2L;

    @Test
    void crearDejaLaVersionUnoInactivaConLosValoresPorDefecto() {
        var catalog = savedCatalog();

        var change = catalog.create("Ofensiva", null, null, ALL_GOALS, Map.of());

        var created = change.catalog().findByName("Ofensiva");
        assertThat(created.versions()).hasSize(1);
        var first = created.latest();
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.scaleFactor()).isEqualTo(new BigDecimal("10.00"));
        assertThat(first.minimumValue()).isEqualTo(new BigDecimal("10.00"));
        assertThat(change.versionsToInsert()).containsExactly(first);
        assertThat(change.changesActive()).isFalse();
        assertThat(change.catalog().activeVersion().id()).isEqualTo(ACTIVE_ID);
    }

    @Test
    void crearRespetaFactorYMinimoInformados() {
        var change = savedCatalog().create("Ofensiva", new BigDecimal("5"), new BigDecimal("1.50"),
                ALL_GOALS, Map.of(Position.DEFENDER, ALL_TACKLES));

        var first = change.catalog().findByName("Ofensiva").latest();
        assertThat(first.scaleFactor()).isEqualTo(new BigDecimal("5.00"));
        assertThat(first.minimumValue()).isEqualTo(new BigDecimal("1.50"));
        assertThat(first.weightsFor(Position.DEFENDER)).isEqualTo(ALL_TACKLES);
    }

    @Test
    void crearGuardaElNombreSinEspaciosDeLosExtremos() {
        var change = savedCatalog().create("  Ofensiva  ", null, null, ALL_GOALS, Map.of());

        assertThat(change.versionsToInsert().get(0).name()).isEqualTo("Ofensiva");
    }

    @Test
    void enUnCatalogoVacioLaPrimeraEstrategiaQuedaActiva() {
        var change = StrategyCatalog.empty().create("Rendimiento", null, null, ALL_GOALS, Map.of());

        var first = change.versionsToInsert().get(0);
        assertThat(change.activated()).contains(first);
        assertThat(change.deactivated()).isEmpty();
        assertThat(change.catalog().activeVersion()).isEqualTo(first);
    }

    @Test
    void rechazaUnNombreRepetidoConOtraCapitalizacionYEspacios() {
        var catalog = savedCatalog();

        assertThatThrownBy(() -> catalog.create("  activa ", null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(DuplicateStrategyNameException.class)
                .hasMessage("Ya existe una estrategia con el nombre 'activa'.");
    }

    @Test
    void rechazaUnNombreVacioConBadRequest() {
        var catalog = savedCatalog();

        assertThatThrownBy(() -> catalog.create("   ", null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidStrategyNameException.class)
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El nombre de la estrategia es obligatorio.");
        assertThatThrownBy(() -> catalog.create(null, null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidStrategyNameException.class);
    }

    @Test
    void rechazaUnNombreDeMasDeCienCaracteres() {
        var catalog = savedCatalog();

        assertThatThrownBy(() -> catalog.create("x".repeat(101), null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(InvalidStrategyNameException.class)
                .hasMessage("El nombre de la estrategia admite a lo sumo 100 caracteres.");
    }

    @Test
    void newVersionDeUnaInactivaNoCambiaLaActiva() {
        var catalog = savedCatalog();

        var change = catalog.newVersion(INACTIVE_ID, null, null, ALL_TACKLES, Map.of());

        var edited = change.catalog().find(INACTIVE_ID);
        assertThat(edited.versions()).extracting(StrategyVersion::version).containsExactly(1, 2);
        assertThat(edited.latest().strategyId()).isEqualTo(INACTIVE_ID);
        assertThat(change.versionsToInsert()).containsExactly(edited.latest());
        assertThat(change.changesActive()).isFalse();
        assertThat(change.catalog().activeVersion().id()).isEqualTo(ACTIVE_ID);
    }

    @Test
    void newVersionDeLaActivaDejaActivaLaNueva() {
        var catalog = savedCatalog();

        var change = catalog.newVersion(ACTIVE_ID, null, null, ALL_TACKLES, Map.of());

        var next = change.catalog().find(ACTIVE_ID).latest();
        assertThat(next.version()).isEqualTo(2);
        assertThat(change.activated()).contains(next);
        assertThat(change.deactivated()).contains(ACTIVE_ID);
        assertThat(change.catalog().activeVersion()).isEqualTo(next);
        assertThat(change.catalog().isActive(ACTIVE_ID)).isTrue();
    }

    @Test
    void newVersionSinFactorNiMinimoHeredaLosDeLaVersionAnterior() {
        var catalog = new StrategyCatalog(List.of(
                strategy(ACTIVE_ID, "Activa", version(ACTIVE_ID, ACTIVE_ID, "Activa", 1, "7.50", "3.25"))), ACTIVE_ID);

        var change = catalog.newVersion(ACTIVE_ID, null, null, ALL_TACKLES, Map.of());

        var next = change.catalog().find(ACTIVE_ID).latest();
        assertThat(next.scaleFactor()).isEqualTo(new BigDecimal("7.50"));
        assertThat(next.minimumValue()).isEqualTo(new BigDecimal("3.25"));
        assertThat(next.defaultWeights()).isEqualTo(ALL_TACKLES);
    }

    @Test
    void newVersionConservaIntactasLasVersionesAnteriores() {
        var catalog = savedCatalog();
        var before = catalog.find(INACTIVE_ID).latest();

        var change = catalog.newVersion(INACTIVE_ID, new BigDecimal("20"), null, ALL_TACKLES, Map.of());

        assertThat(change.catalog().find(INACTIVE_ID).versions().get(0)).isEqualTo(before);
        assertThat(catalog.find(INACTIVE_ID).versions()).hasSize(1);
    }

    @Test
    void activateDejaActivaLaUltimaVersionYDesactivaLaAnterior() {
        var inactive = strategy(INACTIVE_ID, "Inactiva",
                version(INACTIVE_ID, INACTIVE_ID, "Inactiva", 1, "10", "10"),
                version(7L, INACTIVE_ID, "Inactiva", 2, "10", "10"));
        var catalog = new StrategyCatalog(List.of(activeStrategy(), inactive), ACTIVE_ID);

        var change = catalog.activate(INACTIVE_ID);

        assertThat(change.activated()).map(StrategyVersion::id).contains(7L);
        assertThat(change.deactivated()).contains(ACTIVE_ID);
        assertThat(change.versionsToInsert()).isEmpty();
        assertThat(change.catalog().activeVersion().id()).isEqualTo(7L);
        assertThat(change.catalog().isActive(INACTIVE_ID)).isTrue();
        assertThat(change.catalog().isActive(ACTIVE_ID)).isFalse();
    }

    @Test
    void activarLaYaActivaNoCambiaNada() {
        var catalog = savedCatalog();

        var change = catalog.activate(ACTIVE_ID);

        assertThat(change.isEmpty()).isTrue();
        assertThat(change.catalog()).isSameAs(catalog);
    }

    @Test
    void unIdInexistenteSeRechazaConNotFound() {
        var catalog = savedCatalog();

        assertThatThrownBy(() -> catalog.activate(99L))
                .isInstanceOf(StrategyNotFoundException.class)
                .hasMessage("No existe una estrategia con id 99.");
        assertThatThrownBy(() -> catalog.newVersion(99L, null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(StrategyNotFoundException.class);
        assertThatThrownBy(() -> catalog.find(99L))
                .isInstanceOf(StrategyNotFoundException.class);
    }

    @Test
    void siempreHayExactamenteUnaActivaTrasCadaOperacion() {
        var catalog = savedCatalog();

        var afterCreate = catalog.create("Nueva", null, null, ALL_GOALS, Map.of()).catalog();
        var afterEdit = catalog.newVersion(ACTIVE_ID, null, null, ALL_TACKLES, Map.of()).catalog();
        var afterActivate = catalog.activate(INACTIVE_ID).catalog();

        assertThat(List.of(afterCreate, afterEdit, afterActivate)).allSatisfy(result -> {
            var activeCount = result.strategies().stream().filter(s -> result.isActive(s.id())).count();
            assertThat(activeCount).isEqualTo(1);
        });
    }

    @Test
    void elConstructorExigeUnaVersionActiva() {
        var strategies = List.of(activeStrategy(), inactiveStrategy());

        assertThatThrownBy(() -> new StrategyCatalog(strategies, null))
                .isInstanceOf(StrategyInvariantException.class);
        assertThatThrownBy(() -> new StrategyCatalog(strategies, 99L))
                .isInstanceOf(StrategyInvariantException.class);
    }

    @Test
    void elConstructorExigeQueLaActivaSeaLaUltimaVersionDeSuEstrategia() {
        var withTwoVersions = strategy(ACTIVE_ID, "Activa",
                version(ACTIVE_ID, ACTIVE_ID, "Activa", 1, "10", "10"),
                version(5L, ACTIVE_ID, "Activa", 2, "10", "10"));

        assertThatThrownBy(() -> new StrategyCatalog(List.of(withTwoVersions), ACTIVE_ID))
                .isInstanceOf(StrategyInvariantException.class);
    }

    @Test
    void elConstructorRechazaDosEstrategiasConElMismoNombre() {
        var duplicated = strategy(INACTIVE_ID, " ACTIVA", version(INACTIVE_ID, INACTIVE_ID, " ACTIVA", 1, "10", "10"));

        assertThatThrownBy(() -> new StrategyCatalog(List.of(activeStrategy(), duplicated), ACTIVE_ID))
                .isInstanceOf(StrategyInvariantException.class);
    }

    @Test
    void unCatalogoVacioNoTieneActiva() {
        var catalog = StrategyCatalog.empty();

        assertThat(catalog.isActive(ACTIVE_ID)).isFalse();
        assertThatThrownBy(catalog::activeVersion).isInstanceOf(StrategyInvariantException.class);
    }

    private static StrategyCatalog savedCatalog() {
        return new StrategyCatalog(List.of(activeStrategy(), inactiveStrategy()), ACTIVE_ID);
    }

    private static Strategy activeStrategy() {
        return strategy(ACTIVE_ID, "Activa", version(ACTIVE_ID, ACTIVE_ID, "Activa", 1, "10", "10"));
    }

    private static Strategy inactiveStrategy() {
        return strategy(INACTIVE_ID, "Inactiva", version(INACTIVE_ID, INACTIVE_ID, "Inactiva", 1, "10", "10"));
    }

    private static Strategy strategy(long id, String name, StrategyVersion... versions) {
        return new Strategy(id, name, List.of(versions));
    }

    private static StrategyVersion version(long id, long strategyId, String name, int number,
                                           String factor, String minimum) {
        return new StrategyVersion(id, strategyId, name, number, new BigDecimal(factor), new BigDecimal(minimum),
                ALL_GOALS, Map.of(), null);
    }
}
