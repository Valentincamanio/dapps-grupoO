package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StrategySeederTest {
    private static final long MATCH_PERFORMANCE_ID = 1L;
    private static final long BALANCED_ID = 2L;
    private static final long MATCH_PERFORMANCE_V2_ID = 3L;
    private static final WeightSet ALL_GOALS = new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE));

    @Mock
    private StrategyRepository strategyRepository;

    private final ApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void enUnaBaseVaciaCreaLasDosConLaADeActiva(CapturedOutput output) {
        when(strategyRepository.findCatalog()).thenReturn(StrategyCatalog.empty());
        when(strategyRepository.save(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        seeder().run(args);

        ArgumentCaptor<StrategyChange> changes = ArgumentCaptor.forClass(StrategyChange.class);
        verify(strategyRepository, times(2)).save(changes.capture());
        StrategyChange first = changes.getAllValues().get(0);
        StrategyChange second = changes.getAllValues().get(1);
        assertThat(first.versionsToInsert()).extracting(StrategyVersion::name).containsExactly(MATCH_PERFORMANCE_NAME);
        assertThat(first.activated()).map(StrategyVersion::name).contains(MATCH_PERFORMANCE_NAME);
        assertThat(second.versionsToInsert()).extracting(StrategyVersion::name).containsExactly(BALANCED_BY_POSITION_NAME);
        assertThat(second.changesActive()).isFalse();
        assertThat(second.catalog().activeVersion().name()).isEqualTo(MATCH_PERFORMANCE_NAME);
        assertThat(output.getAll()).contains("Se crearon las estrategias de arranque");
    }

    @Test
    void conLasDosExistentesNoGuardaNada(CapturedOutput output) {
        when(strategyRepository.findCatalog()).thenReturn(new StrategyCatalog(List.of(
                strategy(MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE, MATCH_PERFORMANCE_ID),
                strategy(BALANCED_ID, BALANCED_BY_POSITION, BALANCED_ID)), MATCH_PERFORMANCE_ID));

        seeder().run(args);

        verify(strategyRepository, never()).save(any(StrategyChange.class));
        assertThat(output.getAll()).contains("ya existían y no se modificaron");
    }

    @Test
    void conLaAEditadaNoLaTocaAunqueTengaOtrosPesos() {
        var editedFirst = version(MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE_NAME, 1,
                MATCH_PERFORMANCE.defaultWeights());
        var editedSecond = version(MATCH_PERFORMANCE_V2_ID, MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE_NAME, 2,
                ALL_GOALS);
        var editedA = new Strategy(MATCH_PERFORMANCE_ID, MATCH_PERFORMANCE_NAME, List.of(editedFirst, editedSecond));
        when(strategyRepository.findCatalog()).thenReturn(new StrategyCatalog(List.of(
                editedA, strategy(BALANCED_ID, BALANCED_BY_POSITION, BALANCED_ID)), MATCH_PERFORMANCE_V2_ID));

        seeder().run(args);

        verify(strategyRepository, never()).save(any(StrategyChange.class));
    }

    @Test
    void conUnNombreIgualSalvoMayusculasYEspaciosNoLaDuplica() {
        var renamed = new StartingStrategies.Definition("  rendimiento DE partido ", MATCH_PERFORMANCE.scaleFactor(),
                MATCH_PERFORMANCE.minimumValue(), MATCH_PERFORMANCE.defaultWeights(), Map.of());
        when(strategyRepository.findCatalog()).thenReturn(new StrategyCatalog(List.of(
                strategy(MATCH_PERFORMANCE_ID, renamed, MATCH_PERFORMANCE_ID)), MATCH_PERFORMANCE_ID));
        when(strategyRepository.save(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        seeder().run(args);

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        verify(strategyRepository).save(change.capture());
        assertThat(change.getValue().versionsToInsert())
                .extracting(StrategyVersion::name)
                .containsExactly(BALANCED_BY_POSITION_NAME);
        assertThat(change.getValue().changesActive()).isFalse();
    }

    private StrategySeeder seeder() {
        return new StrategySeeder(strategyRepository);
    }

    private static Strategy strategy(long id, StartingStrategies.Definition definition, long versionId) {
        var first = new StrategyVersion(versionId, id, definition.name().strip(), 1, definition.scaleFactor(),
                definition.minimumValue(), definition.defaultWeights(), definition.positionWeights(), null);
        return new Strategy(id, definition.name().strip(), List.of(first));
    }

    private static StrategyVersion version(long id, long strategyId, String name, int number, WeightSet weights) {
        return new StrategyVersion(id, strategyId, name, number, BigDecimal.TEN, BigDecimal.TEN, weights, Map.of(), null);
    }
}
