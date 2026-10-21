package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationLock;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;

/**
 * Activar y editar con el comportamiento por defecto (la semana siguiente): cambia la activa sin
 * recotizar ni tomar candados, y vacía el caché después de guardar solo si la activa cambió.
 */
@ExtendWith(MockitoExtension.class)
class StrategyChangeServiceTest {
    private static final long MATCH_PERFORMANCE_ID = 1L;
    private static final long BALANCED_ID = 2L;
    private static final long MISSING_ID = 99L;
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private StrategyChangeWriter changeWriter;

    @Mock
    private QuoteCacheInvalidator cacheInvalidator;

    @Mock
    private RecalculationLock recalculationLock;

    @Mock
    private SyncLock syncLock;

    @InjectMocks
    private StrategyChangeService changeService;

    @Test
    void activarOtraEstrategiaCambiaLaActivaYDespuesDeGuardarVaciaElCache() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyDetail activated = changeService.activate(BALANCED_ID);

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        InOrder order = inOrder(changeWriter, cacheInvalidator);
        order.verify(changeWriter).apply(change.capture());
        order.verify(cacheInvalidator).invalidateAll();
        assertThat(change.getValue().versionsToInsert()).isEmpty();
        assertThat(change.getValue().activatedVersion().id()).isEqualTo(BALANCED_ID);
        assertThat(change.getValue().deactivatedVersionId()).isEqualTo(MATCH_PERFORMANCE_ID);
        assertThat(activated.strategy().id()).isEqualTo(BALANCED_ID);
        assertThat(activated.active()).isTrue();
        assertThat(change.getValue().catalog().activeVersion().strategyId()).isEqualTo(BALANCED_ID);
    }

    @Test
    void editarLaActivaDejaActivaLaVersionNuevaYVaciaElCache() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyDetail edited = changeService.createVersion(MATCH_PERFORMANCE_ID, new BigDecimal("8"), null,
                ALL_GOALS, Map.of());

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        InOrder order = inOrder(changeWriter, cacheInvalidator);
        order.verify(changeWriter).apply(change.capture());
        order.verify(cacheInvalidator).invalidateAll();
        StrategyVersion second = change.getValue().versionsToInsert().getFirst();
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.scaleFactor()).isEqualByComparingTo("8");
        assertThat(second.minimumValue()).isEqualByComparingTo(MATCH_PERFORMANCE.minimumValue());
        assertThat(change.getValue().activatedVersion()).isEqualTo(second);
        assertThat(change.getValue().deactivatedVersionId()).isEqualTo(MATCH_PERFORMANCE_ID);
        assertThat(edited.active()).isTrue();
        assertThat(edited.strategy().versions()).extracting(StrategyVersion::version).containsExactly(1, 2);
    }

    @Test
    void editarUnaInactivaGuardaLaVersionNuevaSinCambiarLaActivaNiVaciarElCache() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyDetail edited = changeService.createVersion(BALANCED_ID, null, null, ALL_GOALS, Map.of());

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        verify(changeWriter).apply(change.capture());
        assertThat(change.getValue().versionsToInsert()).singleElement()
                .satisfies(version -> assertThat(version.version()).isEqualTo(2));
        assertThat(change.getValue().changesActive()).isFalse();
        assertThat(change.getValue().catalog().activeVersion().id()).isEqualTo(MATCH_PERFORMANCE_ID);
        assertThat(edited.active()).isFalse();
        verify(cacheInvalidator, never()).invalidateAll();
    }

    @Test
    void activarLaQueYaEsLaActivaNoGuardaNadaNiVaciaElCache() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        StrategyDetail activated = changeService.activate(MATCH_PERFORMANCE_ID);

        assertThat(activated.strategy().id()).isEqualTo(MATCH_PERFORMANCE_ID);
        assertThat(activated.active()).isTrue();
        verifyNoInteractions(changeWriter, cacheInvalidator);
    }

    @Test
    void activarOEditarUnaInexistenteRespondeNotFoundSinGuardarNada() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        assertThatThrownBy(() -> changeService.activate(MISSING_ID)).isInstanceOf(StrategyNotFoundException.class);
        assertThatThrownBy(() -> changeService.createVersion(MISSING_ID, null, null, ALL_GOALS, Map.of()))
                .isInstanceOf(StrategyNotFoundException.class);
        verifyNoInteractions(changeWriter, cacheInvalidator);
    }

    @Test
    void ningunCambioTomaElCandadoDelRecalculoNiElDeLaSincronizacion() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        changeService.activate(BALANCED_ID);
        changeService.createVersion(MATCH_PERFORMANCE_ID, null, null, ALL_GOALS, Map.of());

        verifyNoInteractions(recalculationLock, syncLock);
    }

    private static StrategyCatalog startingCatalog() {
        return new StrategyCatalog(List.of(
                strategy(MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE),
                strategy(BALANCED_ID, BALANCED_BY_POSITION)), MATCH_PERFORMANCE_ID);
    }

    private static Strategy strategy(long id, StartingStrategies.Definition definition) {
        var first = new StrategyVersion(id, id, definition.name(), 1, definition.scaleFactor(),
                definition.minimumValue(), definition.defaultWeights(), definition.positionWeights(), null);
        return new Strategy(id, definition.name(), List.of(first));
    }
}
