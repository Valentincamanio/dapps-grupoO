package ar.edu.unq.desapp.futbolmarket.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.OrderNotOwnedException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncDisabledException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.shared.ConflictException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * El aspecto de métricas de negocio con el join point mockeado (research D6 de la 012): devuelve lo
 * mismo que {@code proceed()}, relanza la misma instancia, registra según la tabla de D6 y una falla
 * del registro no cambia el resultado (FR-018).
 */
class BusinessMetricsAspectTest {

    private static final long USER_ID = 7;
    private static final long ORDER_ID = 12;
    private static final List<String> NAMES = List.of(BusinessMetrics.ORDERS, BusinessMetrics.RECALCULATION,
            BusinessMetrics.SYNC, BusinessMetrics.LOGINS, BusinessMetrics.REGISTRATIONS);

    private SimpleMeterRegistry registry;
    private BusinessMetricsAspect aspect;

    @BeforeEach
    void prepare() {
        registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics();
        metrics.bindTo(registry);
        aspect = new BusinessMetricsAspect(metrics);
    }

    @Test
    void unaCompraAceptadaDevuelveElMismoObjetoYSumaUnSuccess() throws Throwable {
        Object outcome = new Object();
        ProceedingJoinPoint joinPoint = returning(outcome);

        Object result = aspect.measureBuy(joinPoint);

        assertThat(result).isSameAs(outcome);
        assertThat(order("buy", "success")).isEqualTo(1);
        assertThat(total()).isEqualTo(1);
    }

    @Test
    void unaCompraSinSaldoRelanzaLaMismaExcepcionYSumaUnRejected() throws Throwable {
        RuntimeException failure = new InsufficientCreditsException(BigDecimal.TEN, BigDecimal.ONE);

        Throwable thrown = catchThrowable(() -> aspect.measureBuy(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(order("buy", "rejected")).isEqualTo(1);
        assertThat(total()).isEqualTo(1);
    }

    @Test
    void unaVentaRechazadaPorNoEncontradoSumaUnRejected() throws Throwable {
        RuntimeException failure = new OrderNotFoundException(ORDER_ID);

        Throwable thrown = catchThrowable(() -> aspect.measureSell(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(order("sell", "rejected")).isEqualTo(1);
    }

    @Test
    void cancelarUnaOrdenAjenaSumaUnRejected() throws Throwable {
        RuntimeException failure = new OrderNotOwnedException();

        Throwable thrown = catchThrowable(() -> aspect.measureCancel(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(order("cancel", "rejected")).isEqualTo(1);
    }

    @Test
    void unaInvarianteDelMercadoNoRegistraNada() throws Throwable {
        RuntimeException failure = new MarketInvariantException("Reserva negativa.");

        Throwable thrown = catchThrowable(() -> aspect.measureBuy(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(total()).isZero();
    }

    @Test
    void unLoginCorrectoSumaUnSuccessYUnoFallidoUnFailure() throws Throwable {
        Object token = new Object();
        RuntimeException failure = new InvalidCredentialsException();

        Object result = aspect.measureLogin(returning(token));
        Throwable thrown = catchThrowable(() -> aspect.measureLogin(throwing(failure)));

        assertThat(result).isSameAs(token);
        assertThat(thrown).isSameAs(failure);
        assertThat(login("success")).isEqualTo(1);
        assertThat(login("failure")).isEqualTo(1);
    }

    @Test
    void unRegistroExitosoSumaUnoYUnoRechazadoNoSuma() throws Throwable {
        RuntimeException duplicated = new ConflictException("Usuario duplicado.");

        aspect.measureRegistration(returning(new Object()));
        Throwable thrown = catchThrowable(() -> aspect.measureRegistration(throwing(duplicated)));

        assertThat(thrown).isSameAs(duplicated);
        assertThat(registry.get(BusinessMetrics.REGISTRATIONS).counter().count()).isEqualTo(1);
        assertThat(total()).isEqualTo(1);
    }

    @Test
    void unRecalculoManualCompletoEsSuccess() throws Throwable {
        RecalculationReport report = recalculationReport(false);
        ProceedingJoinPoint joinPoint = returning(report, RecalculationOrigin.MANUAL);

        Object result = aspect.measureRecalculation(joinPoint);

        assertThat(result).isSameAs(report);
        assertThat(recalculation("manual", "success")).isEqualTo(1);
    }

    @Test
    void unRecalculoSemanalInterrumpidoEsFailure() throws Throwable {
        aspect.measureRecalculation(returning(recalculationReport(true), RecalculationOrigin.WEEKLY));

        assertThat(recalculation("scheduled", "failure")).isEqualTo(1);
    }

    @Test
    void unRecalculoConOtroEnCursoOConUnaSincronizacionEsBlocked() throws Throwable {
        RuntimeException inProgress = new RecalculationInProgressException();
        RuntimeException bySync = new RecalculationBlockedBySyncException();

        Throwable first = catchThrowable(() -> aspect.measureRecalculation(
                throwing(inProgress, RecalculationOrigin.STARTUP)));
        Throwable second = catchThrowable(() -> aspect.measureRecalculation(
                throwing(bySync, RecalculationOrigin.STARTUP)));

        assertThat(first).isSameAs(inProgress);
        assertThat(second).isSameAs(bySync);
        assertThat(recalculation("startup", "blocked")).isEqualTo(2);
    }

    @Test
    void unRecalculoQueAbortaEsFailure() throws Throwable {
        RuntimeException failure = new MarketInvariantException("Falla inesperada.");

        Throwable thrown = catchThrowable(() -> aspect.measureRecalculation(
                throwing(failure, RecalculationOrigin.MANUAL)));

        assertThat(thrown).isSameAs(failure);
        assertThat(recalculation("manual", "failure")).isEqualTo(1);
    }

    @Test
    void unCambioDeEstrategiaCuentaComoManual() throws Throwable {
        aspect.measureRecalculation(returning(recalculationReport(false), RecalculationOrigin.STRATEGY_CHANGE));

        assertThat(recalculation("manual", "success")).isEqualTo(1);
    }

    @Test
    void unaSincronizacionCompletaDeFootballDataSinLigasFallidasEsSuccess() throws Throwable {
        SyncReport report = mock(SyncReport.class);
        when(report.succeeded()).thenReturn(true);

        Object result = aspect.measureFootballDataFullSync(returning(report));

        assertThat(result).isSameAs(report);
        assertThat(sync(SyncSource.FOOTBALL_DATA, SyncType.FULL, "success")).isEqualTo(1);
    }

    @Test
    void unaSincronizacionDeUnaLigaDeWhoScoredConCorteEsFailure() throws Throwable {
        StatsSyncReport report = mock(StatsSyncReport.class);
        when(report.succeeded()).thenReturn(false);

        aspect.measureWhoScoredLeagueSync(returning(report));

        assertThat(sync(SyncSource.WHOSCORED, SyncType.SINGLE_LEAGUE, "failure")).isEqualTo(1);
    }

    @Test
    void unaSincronizacionConOtraEnCursoEsInProgress() throws Throwable {
        RuntimeException failure = new SyncInProgressException();

        Throwable thrown = catchThrowable(() -> aspect.measureWhoScoredFullSync(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(sync(SyncSource.WHOSCORED, SyncType.FULL, "in_progress")).isEqualTo(1);
    }

    @Test
    void unaSincronizacionQueAbortaEsFailure() throws Throwable {
        RuntimeException failure = new MarketInvariantException("Falla inesperada.");

        Throwable thrown = catchThrowable(() -> aspect.measureFootballDataLeagueSync(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(sync(SyncSource.FOOTBALL_DATA, SyncType.SINGLE_LEAGUE, "failure")).isEqualTo(1);
    }

    @Test
    void footballDataSinTokenNoRegistraNada() throws Throwable {
        RuntimeException failure = new SyncDisabledException();

        Throwable thrown = catchThrowable(() -> aspect.measureFootballDataFullSync(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(total()).isZero();
    }

    @Test
    void unRegistroQueFallaNoCambiaElResultadoNiLaExcepcion() throws Throwable {
        BusinessMetrics broken = mock(BusinessMetrics.class);
        RuntimeException recordingFailure = new IllegalStateException("Registro roto.");
        doThrow(recordingFailure).when(broken).recordOrder(any(), any());
        doThrow(recordingFailure).when(broken).recordLogin(any());
        doThrow(recordingFailure).when(broken).recordSync(any(), any(), any(), anyLong());
        BusinessMetricsAspect brokenAspect = new BusinessMetricsAspect(broken);
        Object outcome = new Object();
        RuntimeException rejected = new InvalidCredentialsException();
        RuntimeException inProgress = new SyncInProgressException();

        Object result = brokenAspect.measureBuy(returning(outcome));
        Throwable loginThrown = catchThrowable(() -> brokenAspect.measureLogin(throwing(rejected)));
        Throwable syncThrown = catchThrowable(() -> brokenAspect.measureFootballDataFullSync(throwing(inProgress)));

        assertThat(result).isSameAs(outcome);
        assertThat(loginThrown).isSameAs(rejected);
        assertThat(syncThrown).isSameAs(inProgress);
    }

    private static ProceedingJoinPoint returning(Object value, Object... args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(value);
        when(joinPoint.getArgs()).thenReturn(argsOr(args));
        return joinPoint;
    }

    private static ProceedingJoinPoint throwing(RuntimeException failure, Object... args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(failure);
        when(joinPoint.getArgs()).thenReturn(argsOr(args));
        return joinPoint;
    }

    private static Object[] argsOr(Object[] args) {
        return args.length == 0 ? new Object[] {USER_ID} : args;
    }

    private static RecalculationReport recalculationReport(boolean interrupted) {
        RecalculationReport report = mock(RecalculationReport.class);
        when(report.wasInterrupted()).thenReturn(interrupted);
        return report;
    }

    private double order(String operation, String result) {
        return registry.get(BusinessMetrics.ORDERS)
                .tag(BusinessMetrics.OPERATION_TAG, operation).tag(BusinessMetrics.RESULT_TAG, result)
                .counter().count();
    }

    private double login(String result) {
        return registry.get(BusinessMetrics.LOGINS).tag(BusinessMetrics.RESULT_TAG, result).counter().count();
    }

    private long recalculation(String trigger, String result) {
        return registry.get(BusinessMetrics.RECALCULATION)
                .tag(BusinessMetrics.TRIGGER_TAG, trigger).tag(BusinessMetrics.RESULT_TAG, result)
                .timer().count();
    }

    private long sync(SyncSource source, SyncType type, String result) {
        return registry.get(BusinessMetrics.SYNC)
                .tag(BusinessMetrics.SOURCE_TAG, source.name().toLowerCase(Locale.ROOT))
                .tag(BusinessMetrics.TYPE_TAG, type.name().toLowerCase(Locale.ROOT))
                .tag(BusinessMetrics.RESULT_TAG, result)
                .timer().count();
    }

    private double total() {
        return registry.getMeters().stream()
                .filter(meter -> NAMES.contains(meter.getId().getName()))
                .mapToDouble(BusinessMetricsAspectTest::countOf)
                .sum();
    }

    private static double countOf(Meter meter) {
        if (meter instanceof Timer timer) {
            return timer.count();
        }
        return ((Counter) meter).count();
    }
}
