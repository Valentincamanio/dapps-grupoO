package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.BETO;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON_MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.GREALISH;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.KANE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_A;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_B;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ZIRKZEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingReason;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulatedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulationResult;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidSimulationRequestException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidMinimumValueException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidScaleFactorException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidWeightsException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.StrategyNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * La simulación con el reloj fijo el lunes 07/09/2026 a la mañana: se reproducen la semana del
 * 17/08 (la del Everton 2 - 1 Manchester United, la única con estadísticas), la del 24/08 y la del
 * 31/08. Juegan Ndiaye (2 goles), Zirkzee (1) y Beto (0); Kane y Grealish no juegan.
 */
@ExtendWith(MockitoExtension.class)
class QuoteSimulationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);
    private static final List<Player> ACTIVE = List.of(NDIAYE, BETO, ZIRKZEE, KANE, GREALISH);

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerMatchStatsRepository statsRepository;

    @Mock
    private QuoteRepository quoteRepository;

    private QuoteSimulationService service;

    @BeforeEach
    void createService() {
        service = new QuoteSimulationService(strategyRepository, playerRepository, matchRepository, statsRepository,
                quoteRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void conEstrategiaYParametrosALaVezRechazaSinLeerNada() {
        assertThatThrownBy(() -> service.simulate(STRATEGY_A.strategyId(), parametersOf(STRATEGY_A), NO_FILTER, 0, 10))
                .isInstanceOf(InvalidSimulationRequestException.class)
                .hasMessage("Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna.");

        verifyNoInteractions(strategyRepository, playerRepository, matchRepository, statsRepository, quoteRepository);
    }

    @Test
    void sinEstrategiaNiParametrosRechazaSinLeerNada() {
        assertThatThrownBy(() -> service.simulate(null, null, NO_FILTER, 0, 10))
                .isInstanceOf(InvalidSimulationRequestException.class);

        verifyNoInteractions(strategyRepository, playerRepository, matchRepository, statsRepository, quoteRepository);
    }

    @Test
    void unaEstrategiaInexistenteDaNotFound() {
        when(strategyRepository.findCatalog()).thenReturn(catalog());

        assertThatThrownBy(() -> service.simulate(99L, null, NO_FILTER, 0, 10))
                .isInstanceOf(StrategyNotFoundException.class)
                .hasMessage("No existe una estrategia con id 99.");

        verifyNoInteractions(playerRepository, matchRepository, statsRepository, quoteRepository);
    }

    @Test
    void unFactorDeEscalaNoPositivoSeRechazaComoAlCrearUnaEstrategia() {
        var parameters = new QuoteSimulationService.Parameters(BigDecimal.ZERO, null,
                STRATEGY_A.defaultWeights(), Map.of());

        assertThatThrownBy(() -> service.simulate(null, parameters, NO_FILTER, 0, 10))
                .isInstanceOf(InvalidScaleFactorException.class)
                .hasMessage("El factor de escala debe ser mayor que 0.");

        verifyNoInteractions(playerRepository, matchRepository, statsRepository, quoteRepository);
    }

    @Test
    void unaCotizacionMinimaMenorAUnCentavoSeRechazaComoAlCrearUnaEstrategia() {
        var parameters = new QuoteSimulationService.Parameters(null, new BigDecimal("0.001"),
                STRATEGY_A.defaultWeights(), Map.of());

        assertThatThrownBy(() -> service.simulate(null, parameters, NO_FILTER, 0, 10))
                .isInstanceOf(InvalidMinimumValueException.class);
    }

    @Test
    void sinPesosPorDefectoSeRechazaComoAlCrearUnaEstrategia() {
        var parameters = new QuoteSimulationService.Parameters(null, null, null, Map.of());

        assertThatThrownBy(() -> service.simulate(null, parameters, NO_FILTER, 0, 10))
                .isInstanceOf(InvalidWeightsException.class);
    }

    @Test
    void pesosQueNoSumanUnoSeRechazanConLaMismaReglaQueUnaEstrategia() {
        assertThatThrownBy(() -> WeightSet.of(WeightSet.DEFAULT_LABEL,
                Map.of("GOALS", new BigDecimal("0.50"), "ASSISTS", new BigDecimal("0.45"))))
                .isInstanceOf(InvalidWeightsException.class);
    }

    @Test
    void conUnaEstrategiaGuardadaUsaSuVersionMasRecienteYLaInforma() {
        StrategyVersion second = new StrategyVersion(3L, STRATEGY_B.strategyId(), STRATEGY_B.name(), 2,
                STRATEGY_B.scaleFactor(), STRATEGY_B.minimumValue(), STRATEGY_B.defaultWeights(),
                STRATEGY_B.positionWeights(), NOW);
        when(strategyRepository.findCatalog()).thenReturn(new StrategyCatalog(List.of(
                new Strategy(STRATEGY_A.strategyId(), STRATEGY_A.name(), List.of(STRATEGY_A)),
                new Strategy(STRATEGY_B.strategyId(), STRATEGY_B.name(), List.of(STRATEGY_B, second))),
                STRATEGY_A.id()));
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_B.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(result.strategy()).isEqualTo(second.ref());
    }

    @Test
    void reproduceTodasLasSemanasCerradasDesdeLaPrimeraConEstadisticasSinLaEnCurso() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(result.weeksSimulated()).isEqualTo(3);
        assertThat(result.pendingWeeks()).isEmpty();
    }

    @Test
    void elGoleadorSubeYElQueNoConvirtioBajaRespectoDeSusPares() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(simulatedOf(result, NDIAYE)).isGreaterThan(Quote.INITIAL_VALUE);
        assertThat(simulatedOf(result, BETO)).isLessThan(Quote.INITIAL_VALUE);
    }

    @Test
    void conParametrosNoInformaEstrategiaYCalculaComoLaGuardadaConLosMismosParametros() {
        givenCatalog();
        givenSeason();
        SimulationResult saved = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        SimulationResult withParameters = service.simulate(null, parametersOf(STRATEGY_A), NO_FILTER, 0, 10);

        assertThat(withParameters.strategy()).isNull();
        assertThat(withParameters.page().content()).isEqualTo(saved.page().content());
    }

    @Test
    void conParametrosSinFactorNiMinimoUsaLosPorDefecto() {
        givenSeason();
        var defaults = new StrategyVersion(9L, 9L, "Por defecto", 1, StrategyVersion.DEFAULT_SCALE_FACTOR,
                StrategyVersion.DEFAULT_MINIMUM_VALUE, STRATEGY_A.defaultWeights(), STRATEGY_A.positionWeights(), NOW);
        SimulationResult explicit = service.simulate(null, parametersOf(defaults), NO_FILTER, 0, 10);

        SimulationResult omitted = service.simulate(null, new QuoteSimulationService.Parameters(null, null,
                STRATEGY_A.defaultWeights(), STRATEGY_A.positionWeights()), NO_FILTER, 0, 10);

        assertThat(omitted.page().content()).isEqualTo(explicit.page().content());
    }

    @Test
    void unJugadorSinCotizacionActualQuedaConActualNulaYDiferenciaContraCien() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        SimulatedQuote beto = rowOf(result, BETO);
        assertThat(beto.currentValue()).isNull();
        assertThat(beto.difference()).isEqualByComparingTo(beto.simulatedValue().subtract(Quote.INITIAL_VALUE));
    }

    @Test
    void unJugadorConCotizacionActualInformaSuValorYLaDiferencia() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        SimulatedQuote ndiaye = rowOf(result, NDIAYE);
        assertThat(ndiaye.currentValue()).isEqualByComparingTo("103.00");
        assertThat(ndiaye.difference()).isEqualByComparingTo(ndiaye.simulatedValue().subtract(new BigDecimal("103.00")));
    }

    @Test
    void ordenaPorSimuladaDescendenteYDesempataPorNombreEId() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(result.page().content()).isSortedAccordingTo(
                Comparator.comparing(SimulatedQuote::simulatedValue, Comparator.reverseOrder())
                        .thenComparing(row -> row.player().name())
                        .thenComparing(row -> row.player().id()));
        assertThat(result.page().content().getFirst().player()).isEqualTo(NDIAYE);
    }

    @Test
    void sinSemanasCerradasTodosQuedanEnCienOrdenadosPorNombreEId() {
        givenCatalog();
        Player otherBeto = QuoteFixtures.player(100L, "Beto", Position.FORWARD, QuoteFixtures.MAN_UTD);
        when(playerRepository.findAllActive()).thenReturn(List.of(NDIAYE, BETO, otherBeto, GREALISH));
        when(matchRepository.findAllFinished()).thenReturn(List.of());
        when(statsRepository.findAll()).thenReturn(List.of());
        when(quoteRepository.findLatestByPlayer()).thenReturn(Map.of());

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(result.weeksSimulated()).isZero();
        assertThat(result.page().content()).extracting(SimulatedQuote::player)
                .containsExactly(otherBeto, BETO, NDIAYE, GREALISH);
        assertThat(result.page().content()).extracting(SimulatedQuote::simulatedValue)
                .containsOnly(Quote.INITIAL_VALUE);
    }

    @Test
    void dosSimulacionesIgualesDanLoMismoAunqueLosDatosLleguenEnOtroOrden() {
        givenCatalog();
        givenSeason();
        SimulationResult first = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);
        when(playerRepository.findAllActive()).thenReturn(ACTIVE.reversed());
        when(statsRepository.findAll()).thenReturn(weekStats().reversed());

        SimulationResult second = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void filtraPorLigaEquipoYPosicionComoElCatalogo() {
        givenCatalog();
        givenSeason();

        SimulationResult bundesliga = service.simulate(STRATEGY_A.strategyId(), null,
                new PlayerFilter(League.BUNDESLIGA, null, null), 0, 10);
        SimulationResult everton = service.simulate(STRATEGY_A.strategyId(), null,
                new PlayerFilter(null, "  Everton   FC ", null), 0, 10);
        SimulationResult midfielders = service.simulate(STRATEGY_A.strategyId(), null,
                new PlayerFilter(League.PREMIER, null, Position.MIDFIELDER), 0, 10);

        assertThat(bundesliga.page().content()).extracting(SimulatedQuote::player).containsExactly(KANE);
        assertThat(everton.page().content()).extracting(SimulatedQuote::player)
                .containsExactlyInAnyOrder(NDIAYE, BETO, GREALISH);
        assertThat(midfielders.page().content()).extracting(SimulatedQuote::player).containsExactly(GREALISH);
    }

    @Test
    void elFiltroNoCambiaLaSimuladaPorqueLosParesSonTodosLosActivos() {
        givenCatalog();
        givenSeason();
        SimulationResult all = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        SimulationResult everton = service.simulate(STRATEGY_A.strategyId(), null,
                new PlayerFilter(null, "Everton FC", null), 0, 10);

        assertThat(simulatedOf(everton, NDIAYE)).isEqualTo(simulatedOf(all, NDIAYE));
    }

    @Test
    void paginaEnMemoriaConLaReglaDelCatalogo() {
        givenCatalog();
        givenSeason();
        List<SimulatedQuote> all = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10).page().content();

        SimulationResult second = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 1, 2);

        assertThat(second.page().content()).containsExactlyElementsOf(all.subList(2, 4));
        assertThat(second.page().totalElements()).isEqualTo(5);
        assertThat(second.page().totalPages()).isEqualTo(3);
        assertThat(second.page().hasPrevious()).isTrue();
        assertThat(second.page().hasNext()).isTrue();
    }

    @Test
    void unaHojaFueraDeRangoVieneVacia() {
        givenCatalog();
        givenSeason();

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 7, 10);

        assertThat(result.page().content()).isEmpty();
        assertThat(result.page().totalElements()).isEqualTo(5);
        assertThat(result.page().hasNext()).isFalse();
    }

    @Test
    void informaLasSemanasQueHoyQuedariaPendientesYNoLasSimula() {
        givenCatalog();
        when(playerRepository.findAllActive()).thenReturn(ACTIVE);
        QuoteWeek third = WEEK.next().next();
        Match withoutStats = QuoteFixtures.finishedMatchWithoutStats("560700", third, QuoteFixtures.ARSENAL,
                QuoteFixtures.LIVERPOOL);
        when(matchRepository.findAllFinished()).thenReturn(List.of(EVERTON_MAN_UTD, withoutStats));
        when(statsRepository.findAll()).thenReturn(weekStats());
        when(quoteRepository.findLatestByPlayer()).thenReturn(Map.of());

        SimulationResult result = service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        assertThat(result.weeksSimulated()).isEqualTo(2);
        assertThat(result.pendingWeeks()).containsExactly(new PendingWeek(third, PendingReason.MISSING_STATS));
    }

    @Test
    void leeCadaCosaUnaSolaVezYNoEscribeNada() {
        givenCatalog();
        givenSeason();

        service.simulate(STRATEGY_A.strategyId(), null, NO_FILTER, 0, 10);

        verify(strategyRepository).findCatalog();
        verify(playerRepository).findAllActive();
        verify(matchRepository).findAllFinished();
        verify(statsRepository).findAll();
        verify(quoteRepository).findLatestByPlayer();
        verifyNoMoreInteractions(strategyRepository, playerRepository, matchRepository, statsRepository,
                quoteRepository);
    }

    private void givenCatalog() {
        when(strategyRepository.findCatalog()).thenReturn(catalog());
    }

    /**
     * Los cinco activos, el Everton - Manchester United de la primera semana con sus estadísticas y
     * Ndiaye con una cotización actual de 103,00.
     */
    private void givenSeason() {
        when(playerRepository.findAllActive()).thenReturn(ACTIVE);
        when(matchRepository.findAllFinished()).thenReturn(List.of(EVERTON_MAN_UTD));
        when(statsRepository.findAll()).thenReturn(weekStats());
        when(quoteRepository.findLatestByPlayer()).thenReturn(Map.of(NDIAYE.id(),
                QuoteFixtures.calculated(NDIAYE.id(), WEEK, "103.00", "100.00", "0.300000", STRATEGY_A)));
    }

    private static List<PlayerMatchStats> weekStats() {
        return List.of(
                stats().forPlayer(NDIAYE).inMatch(EVERTON_MAN_UTD).minutes(90).goals(2).shots(4).rating("7.5").build(),
                stats().forPlayer(BETO).inMatch(EVERTON_MAN_UTD).minutes(70).goals(0).shots(1).rating("6.1").build(),
                stats().forPlayer(ZIRKZEE).inMatch(EVERTON_MAN_UTD).minutes(90).goals(1).shots(2).rating("6.8").build());
    }

    private static StrategyCatalog catalog() {
        return new StrategyCatalog(List.of(
                new Strategy(STRATEGY_A.strategyId(), STRATEGY_A.name(), List.of(STRATEGY_A)),
                new Strategy(STRATEGY_B.strategyId(), STRATEGY_B.name(), List.of(STRATEGY_B))), STRATEGY_A.id());
    }

    private static QuoteSimulationService.Parameters parametersOf(StrategyVersion version) {
        return new QuoteSimulationService.Parameters(version.scaleFactor(), version.minimumValue(),
                version.defaultWeights(), version.positionWeights());
    }

    private static SimulatedQuote rowOf(SimulationResult result, Player player) {
        return result.page().content().stream().filter(row -> row.player().equals(player)).findFirst().orElseThrow();
    }

    private static BigDecimal simulatedOf(SimulationResult result, Player player) {
        return rowOf(result, player).simulatedValue();
    }
}
