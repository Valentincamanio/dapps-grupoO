package ar.edu.unq.desapp.futbolmarket.service.strategy;

import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.BALANCED_BY_POSITION_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies.MATCH_PERFORMANCE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.DuplicateStrategyNameException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {
    private static final long MATCH_PERFORMANCE_ID = 1L;
    private static final long BALANCED_ID = 2L;
    private static final long MISSING_ID = 99L;
    private static final String OFFENSIVE = "Ofensiva";
    private static final WeightSet GOALS_AND_RATING = new WeightSet(Map.of(
            ValuationMetric.GOALS, new BigDecimal("0.60"),
            ValuationMetric.RATING, new BigDecimal("0.40")));
    private static final WeightSet ALL_RATING = new WeightSet(Map.of(ValuationMetric.RATING, BigDecimal.ONE));

    @Mock
    private StrategyRepository strategyRepository;

    @InjectMocks
    private StrategyService strategyService;

    @Test
    void crearGuardaLaVersionUnoInactivaConLosValoresPorDefecto() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(strategyRepository.save(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyDetail created = strategyService.create("  " + OFFENSIVE + " ", null, null, GOALS_AND_RATING,
                Map.of(Position.GOALKEEPER, ALL_RATING));

        ArgumentCaptor<StrategyChange> change = ArgumentCaptor.forClass(StrategyChange.class);
        verify(strategyRepository).save(change.capture());
        assertThat(change.getValue().changesActive()).isFalse();
        assertThat(change.getValue().versionsToInsert()).singleElement().satisfies(version -> {
            assertThat(version.name()).isEqualTo(OFFENSIVE);
            assertThat(version.version()).isEqualTo(1);
            assertThat(version.scaleFactor()).isEqualByComparingTo("10");
            assertThat(version.minimumValue()).isEqualByComparingTo("10.00");
            assertThat(version.defaultWeights()).isEqualTo(GOALS_AND_RATING);
            assertThat(version.weightsFor(Position.GOALKEEPER)).isEqualTo(ALL_RATING);
        });
        assertThat(created.strategy().name()).isEqualTo(OFFENSIVE);
        assertThat(created.strategy().versions()).hasSize(1);
        assertThat(created.active()).isFalse();
    }

    @Test
    void crearConFactorYMinimoLosConserva() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(strategyRepository.save(any(StrategyChange.class))).thenAnswer(call -> call.<StrategyChange>getArgument(0).catalog());

        StrategyDetail created = strategyService.create(OFFENSIVE, new BigDecimal("8"), new BigDecimal("5.50"),
                GOALS_AND_RATING, Map.of());

        StrategyVersion version = created.strategy().latest();
        assertThat(version.scaleFactor()).isEqualByComparingTo("8");
        assertThat(version.minimumValue()).isEqualByComparingTo("5.50");
    }

    @Test
    void crearConUnNombreRepetidoSalvoMayusculasYEspaciosRespondeConflictoSinGuardar() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        assertThatThrownBy(() -> strategyService.create(" rendimiento de PARTIDO ", null, null, GOALS_AND_RATING, Map.of()))
                .isInstanceOf(DuplicateStrategyNameException.class)
                .hasMessage("Ya existe una estrategia con el nombre 'rendimiento de PARTIDO'.");
        verify(strategyRepository, never()).save(any(StrategyChange.class));
    }

    @Test
    void unaViolacionDeIntegridadAlGuardarSeInformaComoNombreRepetido() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());
        when(strategyRepository.save(any(StrategyChange.class)))
                .thenThrow(new DataIntegrityViolationException("ux_strategies_name_version"));

        assertThatThrownBy(() -> strategyService.create(OFFENSIVE, null, null, GOALS_AND_RATING, Map.of()))
                .isInstanceOf(DuplicateStrategyNameException.class)
                .hasMessage("Ya existe una estrategia con el nombre 'Ofensiva'.");
    }

    @Test
    void listarDevuelveLasEstrategiasEnOrdenDeCreacionConLaActivaMarcada() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        List<StrategyDetail> strategies = strategyService.list();

        assertThat(strategies)
                .extracting(detail -> detail.strategy().name(), StrategyDetail::active)
                .containsExactly(
                        tuple(MATCH_PERFORMANCE_NAME, true),
                        tuple(BALANCED_BY_POSITION_NAME, false));
    }

    @Test
    void listarUnCatalogoVacioDevuelveUnaListaVacia() {
        when(strategyRepository.findCatalog()).thenReturn(StrategyCatalog.empty());

        assertThat(strategyService.list()).isEmpty();
    }

    @Test
    void consultarDevuelveLaEstrategiaConSusVersionesYSiEsLaActiva() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        StrategyDetail balanced = strategyService.get(BALANCED_ID);

        assertThat(balanced.strategy().id()).isEqualTo(BALANCED_ID);
        assertThat(balanced.strategy().name()).isEqualTo(BALANCED_BY_POSITION_NAME);
        assertThat(balanced.strategy().versions()).hasSize(1);
        assertThat(balanced.active()).isFalse();
    }

    @Test
    void consultarUnaInexistenteRespondeNotFound() {
        when(strategyRepository.findCatalog()).thenReturn(startingCatalog());

        assertThatThrownBy(() -> strategyService.get(MISSING_ID))
                .isInstanceOf(StrategyNotFoundException.class)
                .hasMessage("No existe una estrategia con id 99.");
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
