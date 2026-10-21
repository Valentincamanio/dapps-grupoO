package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON_MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_A;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_B;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingReason;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;

/**
 * El recálculo con el reloj fijo el lunes 07/09/2026 a la mañana: las semanas cerradas son la del
 * 17/08 (la primera con estadísticas), la del 24/08 y la del 31/08.
 */
@ExtendWith(MockitoExtension.class)
class QuoteRecalculationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final QuoteWeek FIRST = WEEK;
    private static final QuoteWeek SECOND = FIRST.next();
    private static final QuoteWeek THIRD = SECOND.next();

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private QuoteWeekWriter weekWriter;

    @Mock
    private QuoteCacheInvalidator cacheInvalidator;

    private final SyncLock syncLock = new SyncLock();
    private final RecalculationLock recalculationLock = new RecalculationLock(syncLock);
    private QuoteRecalculationService service;

    @BeforeEach
    void createService() {
        service = new QuoteRecalculationService(recalculationLock, strategyRepository, quoteRepository,
                matchRepository, weekWriter, new RecalculationReportLogger(), cacheInvalidator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void cotizaLasSemanasCerradasEnOrdenConLaEstrategiaActiva() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.empty(), List.of(EVERTON_MAN_UTD));
        givenWriterQuotesEveryWeek();

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        var order = inOrder(weekWriter);
        order.verify(weekWriter).quoteWeek(FIRST, STRATEGY_A);
        order.verify(weekWriter).quoteWeek(SECOND, STRATEGY_A);
        order.verify(weekWriter).quoteWeek(THIRD, STRATEGY_A);
        order.verifyNoMoreInteractions();
        assertThat(report.quotedWeeks()).extracting(WeekResult::week).containsExactly(FIRST, SECOND, THIRD);
        assertThat(report.origin()).isEqualTo(RecalculationOrigin.MANUAL);
        assertThat(report.strategy()).isEqualTo(STRATEGY_A.ref());
        assertThat(report.startedAt()).isEqualTo(NOW);
        assertThat(report.wasInterrupted()).isFalse();
    }

    @Test
    void sigueDesdeLaSemanaSiguienteALaUltimaCotizada() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.of(FIRST), List.of(EVERTON_MAN_UTD));
        givenWriterQuotesEveryWeek();

        RecalculationReport report = service.recalculate(RecalculationOrigin.WEEKLY);

        verify(weekWriter, never()).quoteWeek(FIRST, STRATEGY_A);
        assertThat(report.quotedWeeks()).extracting(WeekResult::week).containsExactly(SECOND, THIRD);
    }

    @Test
    void leeLaActivaUnaSolaVezAunqueElCatalogoCambieDuranteLaCorrida() {
        when(strategyRepository.findCatalog())
                .thenReturn(catalogWithActive(STRATEGY_A))
                .thenReturn(catalogWithActive(STRATEGY_B));
        givenHistory(Optional.empty(), List.of(EVERTON_MAN_UTD));
        givenWriterQuotesEveryWeek();

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        verify(strategyRepository).findCatalog();
        verify(weekWriter).quoteWeek(FIRST, STRATEGY_A);
        verify(weekWriter).quoteWeek(SECOND, STRATEGY_A);
        verify(weekWriter).quoteWeek(THIRD, STRATEGY_A);
        verify(weekWriter, never()).quoteWeek(any(QuoteWeek.class), eq(STRATEGY_B));
        assertThat(report.strategy()).isEqualTo(STRATEGY_A.ref());
    }

    @Test
    void anteUnaFallaDeLaBaseCortaSinIntentarLasSiguientesYLoIndicaEnElInforme() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.empty(), List.of(EVERTON_MAN_UTD));
        when(weekWriter.quoteWeek(FIRST, STRATEGY_A)).thenReturn(new WeekResult(FIRST, 11, 11, 0));
        when(weekWriter.quoteWeek(SECOND, STRATEGY_A)).thenThrow(new DataIntegrityViolationException("índice único"));

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        verify(weekWriter, never()).quoteWeek(THIRD, STRATEGY_A);
        assertThat(report.quotedWeeks()).extracting(WeekResult::week).containsExactly(FIRST);
        assertThat(report.interruptedAt()).contains(SECOND);
        assertThat(report.isEmpty()).isFalse();
    }

    @Test
    void sinSemanasParaCotizarDevuelveUnInformeVacioSinErrorYNoVaciaElCache() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.of(THIRD), List.of(EVERTON_MAN_UTD));

        RecalculationReport report = service.recalculate(RecalculationOrigin.STARTUP);

        assertThat(report.isEmpty()).isTrue();
        assertThat(report.pendingWeeks()).isEmpty();
        verifyNoInteractions(weekWriter, cacheInvalidator);
    }

    @Test
    void sinEstadisticasEnNingunaParteNoHayNadaParaCotizar() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.empty(), List.of());

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        assertThat(report.isEmpty()).isTrue();
        verifyNoInteractions(weekWriter, cacheInvalidator);
    }

    @Test
    void lasSemanasPendientesDelPlanVanAlInformeSinCotizarse() {
        Match withoutStats = QuoteFixtures.finishedMatchWithoutStats("560700", THIRD, ARSENAL, LIVERPOOL);
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.empty(), List.of(EVERTON_MAN_UTD, withoutStats));
        givenWriterQuotesEveryWeek();

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        verify(weekWriter, never()).quoteWeek(THIRD, STRATEGY_A);
        assertThat(report.quotedWeeks()).extracting(WeekResult::week).containsExactly(FIRST, SECOND);
        assertThat(report.pendingWeeks()).containsExactly(new PendingWeek(THIRD, PendingReason.MISSING_STATS));
    }

    @Test
    void vaciaElCacheUnaVezDespuesDeCotizarYConElCandadoYaSuelto() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.of(SECOND), List.of(EVERTON_MAN_UTD));
        givenWriterQuotesEveryWeek();
        doAnswer(call -> recalculationLock.runExclusively(() -> null)).when(cacheInvalidator).invalidateAll();

        service.recalculate(RecalculationOrigin.MANUAL);

        var order = inOrder(weekWriter, cacheInvalidator);
        order.verify(weekWriter).quoteWeek(THIRD, STRATEGY_A);
        order.verify(cacheInvalidator).invalidateAll();
    }

    @Test
    void siLaPrimeraSemanaFallaNoVaciaElCache() {
        givenActive(catalogWithActive(STRATEGY_A));
        givenHistory(Optional.of(SECOND), List.of(EVERTON_MAN_UTD));
        when(weekWriter.quoteWeek(THIRD, STRATEGY_A)).thenThrow(new DataIntegrityViolationException("índice único"));

        RecalculationReport report = service.recalculate(RecalculationOrigin.MANUAL);

        assertThat(report.interruptedAt()).contains(THIRD);
        verify(cacheInvalidator, never()).invalidateAll();
    }

    @Test
    void conOtroRecalculoEnCursoPropagaEl409SinCotizarNada() {
        Throwable thrown = recalculationLock.runExclusively(() -> catchThrowable(
                () -> service.recalculate(RecalculationOrigin.MANUAL)));

        assertThat(thrown).isInstanceOf(RecalculationInProgressException.class);
        verifyNoInteractions(strategyRepository, quoteRepository, matchRepository, weekWriter, cacheInvalidator);
    }

    @Test
    void conUnaSincronizacionEnCursoPropagaEl409SinCotizarNada() {
        syncLock.runExclusively(() -> {
            assertThatThrownBy(() -> service.recalculate(RecalculationOrigin.MANUAL))
                    .isInstanceOf(RecalculationBlockedBySyncException.class);
            return null;
        });

        verifyNoInteractions(strategyRepository, quoteRepository, matchRepository, weekWriter, cacheInvalidator);
    }

    @Test
    void conSemanasCerradasSinCotizarLasInformaSinTomarCandadosNiEscribir() {
        givenHistory(Optional.of(FIRST), List.of(EVERTON_MAN_UTD));

        boolean pending = recalculationLock.runExclusively(service::hasClosedUnquotedWeeks);

        assertThat(pending).isTrue();
        verifyNoInteractions(strategyRepository, weekWriter, cacheInvalidator);
    }

    @Test
    void conUnaSemanaPendienteDeEstadisticasTambienHaySemanasSinCotizar() {
        Match withoutStats = QuoteFixtures.finishedMatchWithoutStats("560700", THIRD, ARSENAL, LIVERPOOL);
        givenHistory(Optional.of(SECOND), List.of(EVERTON_MAN_UTD, withoutStats));

        boolean pending = service.hasClosedUnquotedWeeks();

        assertThat(pending).isTrue();
    }

    @Test
    void conLaUltimaSemanaCerradaYaCotizadaNoHaySemanasSinCotizar() {
        givenHistory(Optional.of(THIRD), List.of(EVERTON_MAN_UTD));

        boolean pending = service.hasClosedUnquotedWeeks();

        assertThat(pending).isFalse();
        verifyNoInteractions(strategyRepository, weekWriter, cacheInvalidator);
    }

    @Test
    void sinEstadisticasEnNingunaParteNoHaySemanasSinCotizar() {
        givenHistory(Optional.empty(), List.of());

        boolean pending = service.hasClosedUnquotedWeeks();

        assertThat(pending).isFalse();
    }

    private void givenActive(StrategyCatalog catalog) {
        when(strategyRepository.findCatalog()).thenReturn(catalog);
    }

    private void givenHistory(Optional<QuoteWeek> lastCalculated, List<Match> finishedMatches) {
        when(quoteRepository.findLastCalculatedWeek()).thenReturn(lastCalculated);
        when(matchRepository.findAllFinished()).thenReturn(finishedMatches);
    }

    private void givenWriterQuotesEveryWeek() {
        when(weekWriter.quoteWeek(any(QuoteWeek.class), any(StrategyVersion.class)))
                .thenAnswer(call -> new WeekResult(call.getArgument(0), 0, 11, 0));
    }

    /**
     * Las dos estrategias de arranque, con la indicada como activa.
     */
    private static StrategyCatalog catalogWithActive(StrategyVersion active) {
        return new StrategyCatalog(List.of(
                new Strategy(STRATEGY_A.strategyId(), STRATEGY_A.name(), List.of(STRATEGY_A)),
                new Strategy(STRATEGY_B.strategyId(), STRATEGY_B.name(), List.of(STRATEGY_B))), active.id());
    }
}
