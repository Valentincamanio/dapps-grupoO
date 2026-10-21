package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationLock;
import ar.edu.unq.desapp.futbolmarket.service.quote.RecalculationReportLogger;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;

/**
 * Activar y editar con el comportamiento por defecto (la semana siguiente): cambia la activa sin
 * recotizar ni tomar candados, y vacía el caché después de guardar solo si la activa cambió.
 *
 * <p>Con {@code AHORA} (US5): toma el candado del recálculo afuera de la transacción, lo suelta
 * después del commit y recién entonces vacía el caché; con el candado ocupado no escribe nada.</p>
 */
@ExtendWith(MockitoExtension.class)
class StrategyChangeServiceTest {
    private static final long MATCH_PERFORMANCE_ID = 1L;
    private static final long BALANCED_ID = 2L;
    private static final long MISSING_ID = 99L;
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));
    private static final Instant NOW = Instant.parse("2026-09-09T15:00:00Z");
    private static final QuoteWeek LAST_CLOSED = QuoteWeek.lastClosedAt(NOW);

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

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private RecalculationReportLogger reportLogger;

    @Mock
    private Clock clock;

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

    @Test
    void ahoraTomaElCandadoRecotizaAdentroYLoSueltaAntesDeVaciarElCache() {
        StrategyCatalog activated = startingCatalog().activate(BALANCED_ID).catalog();
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog(), activated);
        when(clock.instant()).thenReturn(NOW);
        AtomicBoolean locked = lockRunsTheAction();
        RecalculationReport report = emptyReport(activated);
        when(changeWriter.applyAndRequote(any(StrategyChange.class), eq(NOW))).thenAnswer(call -> {
            assertThat(locked).isTrue();
            return report;
        });
        doAnswer(call -> {
            assertThat(locked).isFalse();
            return null;
        }).when(cacheInvalidator).invalidateAll();

        StrategyChangeResult result = changeService.activate(BALANCED_ID, RequoteTiming.NOW);

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        InOrder order = inOrder(recalculationLock, changeWriter, cacheInvalidator, reportLogger);
        order.verify(recalculationLock).runExclusively(any());
        order.verify(changeWriter).applyAndRequote(change.capture(), eq(NOW));
        order.verify(cacheInvalidator).invalidateAll();
        order.verify(reportLogger).log(report);
        assertThat(change.getValue().activatedVersion().id()).isEqualTo(BALANCED_ID);
        assertThat(result.requotation()).contains(report);
        assertThat(result.strategy().strategy().id()).isEqualTo(BALANCED_ID);
        assertThat(result.strategy().active()).isTrue();
        verify(changeWriter, never()).apply(any());
    }

    @Test
    void editarLaActivaConAhoraRecotizaConLaVersionNueva() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(clock.instant()).thenReturn(NOW);
        lockRunsTheAction();
        when(changeWriter.applyAndRequote(any(StrategyChange.class), eq(NOW)))
                .thenAnswer(call -> emptyReport(call.<StrategyChange>getArgument(0).catalog()));

        StrategyChangeResult result = changeService.createVersion(MATCH_PERFORMANCE_ID, new BigDecimal("8"), null,
                ALL_GOALS, Map.of(), RequoteTiming.NOW);

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        verify(changeWriter).applyAndRequote(change.capture(), eq(NOW));
        assertThat(change.getValue().activatedVersion().version()).isEqualTo(2);
        assertThat(result.requotation()).isPresent();
        verify(cacheInvalidator).invalidateAll();
        verify(changeWriter, never()).apply(any());
    }

    @Test
    void conUnRecalculoEnCursoAhoraRespondeConflictoSinLlamarAlWriterNiVaciarElCache() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(recalculationLock.runExclusively(any())).thenThrow(new RecalculationInProgressException());

        assertThatThrownBy(() -> changeService.activate(BALANCED_ID, RequoteTiming.NOW))
                .isInstanceOf(RecalculationInProgressException.class);

        verifyNoInteractions(changeWriter, cacheInvalidator, reportLogger);
    }

    @Test
    void conUnaSincronizacionEnCursoAhoraRespondeConflictoSinLlamarAlWriter() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(recalculationLock.runExclusively(any())).thenThrow(new RecalculationBlockedBySyncException());

        assertThatThrownBy(() -> changeService.createVersion(MATCH_PERFORMANCE_ID, null, null, ALL_GOALS, Map.of(),
                RequoteTiming.NOW)).isInstanceOf(RecalculationBlockedBySyncException.class);

        verifyNoInteractions(changeWriter, cacheInvalidator, reportLogger);
    }

    @Test
    void laSemanaSiguienteYLaOpcionAusenteNoTomanCandadoNiRecotizan() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyChangeResult nextWeek = changeService.activate(BALANCED_ID, RequoteTiming.NEXT_WEEK);
        StrategyChangeResult absent = changeService.createVersion(MATCH_PERFORMANCE_ID, null, null, ALL_GOALS,
                Map.of(), RequoteTiming.fromWire(null));

        assertThat(nextWeek.requotation()).isEmpty();
        assertThat(absent.requotation()).isEmpty();
        verify(changeWriter, never()).applyAndRequote(any(), any());
        verifyNoInteractions(recalculationLock, syncLock, quoteRepository, reportLogger);
    }

    @Test
    void ahoraSeIgnoraAlEditarUnaQueNoEsLaActiva() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(changeWriter.apply(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyChangeResult result = changeService.createVersion(BALANCED_ID, null, null, ALL_GOALS, Map.of(),
                RequoteTiming.NOW);

        verify(changeWriter).apply(any(StrategyChange.class));
        assertThat(result.requotation()).isEmpty();
        assertThat(result.strategy().active()).isFalse();
        verify(changeWriter, never()).applyAndRequote(any(), any());
        verifyNoInteractions(recalculationLock, cacheInvalidator, reportLogger);
    }

    @Test
    void activarConAhoraLaActivaEnLaVersionQueCotizoLaUltimaSemanaNoHaceNada() {
        StrategyCatalog catalog = startingCatalog();
        when(strategyRepository.findCatalog()).thenReturn(catalog);
        when(clock.instant()).thenReturn(NOW);
        when(quoteRepository.findLatestOfWeek(LAST_CLOSED)).thenReturn(Map.of(
                7L, QuoteFixtures.calculated(7L, LAST_CLOSED, "103.00", "100.00", "0.300000", catalog.activeVersion())));

        StrategyChangeResult result = changeService.activate(MATCH_PERFORMANCE_ID, RequoteTiming.NOW);

        assertThat(result.requotation()).isEmpty();
        assertThat(result.strategy().active()).isTrue();
        verifyNoInteractions(changeWriter, recalculationLock, cacheInvalidator, reportLogger);
    }

    @Test
    void activarConAhoraLaActivaSinCotizacionesEnLaUltimaSemanaNoHaceNada() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(clock.instant()).thenReturn(NOW);
        when(quoteRepository.findLatestOfWeek(LAST_CLOSED)).thenReturn(Map.of());

        StrategyChangeResult result = changeService.activate(MATCH_PERFORMANCE_ID, RequoteTiming.NOW);

        assertThat(result.requotation()).isEmpty();
        verifyNoInteractions(changeWriter, recalculationLock, cacheInvalidator, reportLogger);
    }

    @Test
    void activarConAhoraLaActivaCuandoLaUltimaSemanaSeCotizoConOtraVersionRecotiza() {
        StrategyCatalog catalog = startingCatalog();
        StrategyVersion other = catalog.find(BALANCED_ID).latest();
        when(strategyRepository.findCatalog()).thenReturn(catalog);
        when(clock.instant()).thenReturn(NOW);
        when(quoteRepository.findLatestOfWeek(LAST_CLOSED)).thenReturn(Map.of(
                7L, QuoteFixtures.calculated(7L, LAST_CLOSED, "103.00", "100.00", "0.300000", other)));
        lockRunsTheAction();
        when(changeWriter.applyAndRequote(any(StrategyChange.class), eq(NOW))).thenReturn(emptyReport(catalog));

        StrategyChangeResult result = changeService.activate(MATCH_PERFORMANCE_ID, RequoteTiming.NOW);

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        verify(changeWriter).applyAndRequote(change.capture(), eq(NOW));
        assertThat(change.getValue().isEmpty()).isTrue();
        assertThat(result.requotation()).isPresent();
        verify(cacheInvalidator).invalidateAll();
    }

    /**
     * El candado corre la acción y deja marcado si está tomado, para ver qué pasa adentro y qué
     * después de soltarlo.
     */
    private AtomicBoolean lockRunsTheAction() {
        AtomicBoolean locked = new AtomicBoolean(false);
        when(recalculationLock.runExclusively(any())).thenAnswer(call -> {
            locked.set(true);
            try {
                return call.<Supplier<?>>getArgument(0).get();
            } finally {
                locked.set(false);
            }
        });
        return locked;
    }

    private static RecalculationReport emptyReport(StrategyCatalog catalog) {
        return RecalculationReport.builder(RecalculationOrigin.STRATEGY_CHANGE, NOW, catalog.activeVersion().ref())
                .build(NOW);
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
