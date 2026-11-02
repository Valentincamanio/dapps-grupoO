package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.ActiveStrategyDeletionException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La baja lógica de una estrategia: se conserva en el catálogo renombrada, no se lista ni se puede
 * usar, y su nombre queda libre. El instante de la baja es el domingo 01/11/2026 a las 14:35 de
 * Argentina.
 */
class StrategyCatalogDeletionTest {
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));
    private static final Instant NOW = Instant.parse("2026-11-01T17:35:00Z");
    private static final String SUFFIX = " (eliminada el 01/11/2026 14:35)";

    private static final long ACTIVE_ID = 1L;
    private static final long INACTIVE_ID = 2L;
    private static final long DELETED_ID = 3L;
    private static final long MISSING_ID = 99L;

    @Test
    void eliminarUnaInactivaLaDevuelveEliminadaYRenombradaConLaFechaYHora() {
        var catalog = catalog();

        var deleted = catalog.delete(INACTIVE_ID, NOW);

        assertThat(deleted.id()).isEqualTo(INACTIVE_ID);
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.name()).isEqualTo("Inactiva" + SUFFIX);
        assertThat(deleted.originalName()).isEqualTo("Inactiva");
        assertThat(deleted.versions()).extracting(StrategyVersion::name).containsOnly("Inactiva" + SUFFIX);
        assertThat(deleted.latest().scaleFactor()).isEqualTo(catalog.find(INACTIVE_ID).latest().scaleFactor());
    }

    @Test
    void laActivaNoSeElimina() {
        var catalog = catalog();

        assertThatThrownBy(() -> catalog.delete(ACTIVE_ID, NOW))
                .isInstanceOf(ActiveStrategyDeletionException.class)
                .hasMessage("No se puede eliminar 'Activa' porque es la estrategia activa: primero hay que activar otra.");
    }

    @Test
    void eliminarUnaInexistenteOYaEliminadaRespondeNoEncontrada() {
        var catalog = catalog();

        assertThatThrownBy(() -> catalog.delete(MISSING_ID, NOW)).isInstanceOf(StrategyNotFoundException.class);
        assertThatThrownBy(() -> catalog.delete(DELETED_ID, NOW)).isInstanceOf(StrategyNotFoundException.class);
    }

    @Test
    void elListadoNoMuestraLasEliminadas() {
        var catalog = catalog();

        var listed = catalog.listed();

        assertThat(listed).extracting(Strategy::id).containsExactly(ACTIVE_ID, INACTIVE_ID);
        assertThat(catalog.strategies()).hasSize(3);
    }

    @Test
    void unaEliminadaNoSeEncuentraNiSeEditaNiSeActiva() {
        var catalog = catalog();

        assertThatThrownBy(() -> catalog.find(DELETED_ID)).isInstanceOf(StrategyNotFoundException.class);
        assertThatThrownBy(() -> catalog.newVersion(DELETED_ID, null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(StrategyNotFoundException.class);
        assertThatThrownBy(() -> catalog.activate(DELETED_ID)).isInstanceOf(StrategyNotFoundException.class);
    }

    @Test
    void elNombreDeUnaEliminadaQuedaLibreParaUnaNueva() {
        var catalog = catalog();

        var change = catalog.create(" ELIMINADA ", null, null, ALL_GOALS, Map.of());

        assertThat(change.versionsToInsert()).extracting(StrategyVersion::name).containsExactly("ELIMINADA");
        assertThat(change.catalog().strategies()).extracting(Strategy::name)
                .contains("Eliminada" + SUFFIX, "ELIMINADA");
    }

    @Test
    void conoceLosNombresDeLasQueHayYDeLasQueSeEliminaron() {
        var catalog = catalog();

        var current = catalog.knows("inactiva");
        var deleted = catalog.knows(" eliminada ");
        var never = catalog.knows("Ofensiva");

        assertThat(current).isTrue();
        assertThat(deleted).isTrue();
        assertThat(never).isFalse();
    }

    @Test
    void siYaHayUnaEliminadaConEseNombreEnElMismoMinutoLaNuevaLlevaUnNumero() {
        var catalog = new StrategyCatalog(List.of(
                strategy(ACTIVE_ID, "Activa"),
                strategy(INACTIVE_ID, "Copia"),
                new Strategy(DELETED_ID, "Copia" + SUFFIX, List.of(version(DELETED_ID, "Copia" + SUFFIX)), true,
                        "Copia")), ACTIVE_ID);

        var deleted = catalog.delete(INACTIVE_ID, NOW);

        assertThat(deleted.name()).isEqualTo("Copia (eliminada el 01/11/2026 14:35, 2)");
    }

    @Test
    void unNombreLargoSeRecortaParaQueElRenombradoEntreEnElMaximo() {
        var longName = "N".repeat(StrategyCatalog.MAX_NAME_LENGTH);
        var catalog = new StrategyCatalog(List.of(strategy(ACTIVE_ID, "Activa"), strategy(INACTIVE_ID, longName)),
                ACTIVE_ID);

        var deleted = catalog.delete(INACTIVE_ID, NOW);

        assertThat(deleted.name()).hasSize(StrategyCatalog.MAX_NAME_LENGTH).endsWith(SUFFIX);
        assertThat(deleted.originalName()).isEqualTo(longName);
    }

    @Test
    void editarUnaEstrategiaConservaQueNoEstaEliminada() {
        var catalog = catalog();

        var change = catalog.newVersion(INACTIVE_ID, null, null, ALL_GOALS, Map.of());

        assertThat(change.catalog().find(INACTIVE_ID).deleted()).isFalse();
        assertThat(change.catalog().find(INACTIVE_ID).versions()).hasSize(2);
    }

    private static StrategyCatalog catalog() {
        return new StrategyCatalog(List.of(
                strategy(ACTIVE_ID, "Activa"),
                strategy(INACTIVE_ID, "Inactiva"),
                new Strategy(DELETED_ID, "Eliminada" + SUFFIX, List.of(version(DELETED_ID, "Eliminada" + SUFFIX)),
                        true, "Eliminada")), ACTIVE_ID);
    }

    private static Strategy strategy(long id, String name) {
        return new Strategy(id, name, List.of(version(id, name)));
    }

    private static StrategyVersion version(long id, String name) {
        return new StrategyVersion(id, id, name, 1, BigDecimal.TEN, BigDecimal.TEN, ALL_GOALS, Map.of(), null);
    }
}
