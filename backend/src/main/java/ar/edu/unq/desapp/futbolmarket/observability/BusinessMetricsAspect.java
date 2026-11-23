package ar.edu.unq.desapp.futbolmarket.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
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
import ar.edu.unq.desapp.futbolmarket.shared.ForbiddenException;
import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Registra las métricas de negocio alrededor de los casos de uso (research D5 y D6 de la 012):
 * órdenes del mercado, inicio de sesión, registro, recálculo de cotizaciones y sincronizaciones.
 * Los servicios instrumentados no cambian.
 *
 * <p><b>Ninguna métrica cambia un resultado</b> (FR-018): cada advice devuelve el valor de
 * {@code proceed()} o relanza la misma instancia de la excepción. Clasificar y registrar va dentro de
 * {@link #recordSafely}, que captura {@code RuntimeException} y la deja en un {@code WARN}; un
 * {@code Error} de la JVM no se captura. Una excepción que la tabla de D6 no nombra no registra nada.
 * El {@code throws Throwable} lo impone {@link ProceedingJoinPoint#proceed()}.</p>
 *
 * <p>Tiene la máxima precedencia para quedar <b>afuera</b> de la transacción del servicio: una falla
 * al commitear también se ve y la duración la incluye.</p>
 *
 * <p>Los pointcuts son texto, sin depender de las clases de {@code service/}. {@code runFull} es
 * package-private y se intercepta igual porque el proxy CGLIB se genera en el mismo paquete; por eso
 * su pointcut no lleva {@code public}. Es lo que llama la cadena de {@code SyncCoordinator}. Cuando
 * {@code synchronizeAll} llama a {@code runFull} adentro del mismo servicio es una auto-invocación:
 * no pasa por el proxy y la sincronización cuenta una sola vez.</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class BusinessMetricsAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessMetricsAspect.class);

    private static final String SERVICE = "ar.edu.unq.desapp.futbolmarket.service.";
    private static final String ORDER_SERVICE = SERVICE + "market.MarketOrderService.";
    private static final String AUTH_SERVICE = SERVICE + "auth.AuthService.";
    private static final String SYNC_SERVICE = SERVICE + "sync.SyncService.";
    private static final String STATS_SYNC_SERVICE = SERVICE + "sync.StatsSyncService.";

    private static final String BUY = "execution(public * " + ORDER_SERVICE + "buy(..))";
    private static final String SELL = "execution(public * " + ORDER_SERVICE + "sell(..))";
    private static final String CANCEL = "execution(public * " + ORDER_SERVICE + "cancel(..))";
    private static final String LOGIN = "execution(public * " + AUTH_SERVICE + "login(..))";
    private static final String REGISTER = "execution(public * " + AUTH_SERVICE + "register(..))";
    private static final String RECALCULATE =
            "execution(public * " + SERVICE + "quote.QuoteRecalculationService.recalculate(..))";
    private static final String SYNC_ALL = "execution(public * " + SYNC_SERVICE + "synchronizeAll(..))";
    private static final String SYNC_LEAGUE = "execution(public * " + SYNC_SERVICE + "synchronizeLeague(..))";
    private static final String SYNC_RUN_FULL = "execution(* " + SYNC_SERVICE + "runFull(..))";
    private static final String STATS_SYNC_ALL = "execution(public * " + STATS_SYNC_SERVICE + "synchronizeAll(..))";
    private static final String STATS_SYNC_LEAGUE =
            "execution(public * " + STATS_SYNC_SERVICE + "synchronizeLeague(..))";
    private static final String STATS_SYNC_RUN_FULL = "execution(* " + STATS_SYNC_SERVICE + "runFull(..))";

    private final BusinessMetrics metrics;

    @Around(BUY)
    public Object measureBuy(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureOrder(joinPoint, OrderOperation.BUY);
    }

    @Around(SELL)
    public Object measureSell(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureOrder(joinPoint, OrderOperation.SELL);
    }

    @Around(CANCEL)
    public Object measureCancel(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureOrder(joinPoint, OrderOperation.CANCEL);
    }

    /**
     * Usuario inexistente y contraseña incorrecta lanzan la misma excepción: los dos son
     * {@code failure}.
     */
    @Around(LOGIN)
    public Object measureLogin(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            recordSafely(BusinessMetrics.LOGINS, () -> metrics.recordLogin(LoginResult.SUCCESS));
            return result;
        } catch (InvalidCredentialsException e) {
            recordSafely(BusinessMetrics.LOGINS, () -> metrics.recordLogin(LoginResult.FAILURE));
            throw e;
        }
    }

    /**
     * Solo cuenta el registro que termina bien: duplicados y datos inválidos no suman.
     */
    @Around(REGISTER)
    public Object measureRegistration(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        recordSafely(BusinessMetrics.REGISTRATIONS, metrics::recordRegistration);
        return result;
    }

    /**
     * Los dos rechazos del candado (otro recálculo o una sincronización en curso) son
     * {@code blocked}; un informe interrumpido o cualquier otra excepción, {@code failure}.
     */
    @Around(RECALCULATE)
    public Object measureRecalculation(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            recordRecalculation(joinPoint, outcomeOf(result), start);
            return result;
        } catch (RecalculationInProgressException | RecalculationBlockedBySyncException e) {
            recordRecalculation(joinPoint, RecalculationResult.BLOCKED, start);
            throw e;
        } catch (RuntimeException e) {
            recordRecalculation(joinPoint, RecalculationResult.FAILURE, start);
            throw e;
        }
    }

    @Around(SYNC_ALL + " || " + SYNC_RUN_FULL)
    public Object measureFootballDataFullSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureSync(joinPoint, SyncSource.FOOTBALL_DATA, SyncType.FULL);
    }

    @Around(SYNC_LEAGUE)
    public Object measureFootballDataLeagueSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureSync(joinPoint, SyncSource.FOOTBALL_DATA, SyncType.SINGLE_LEAGUE);
    }

    @Around(STATS_SYNC_ALL + " || " + STATS_SYNC_RUN_FULL)
    public Object measureWhoScoredFullSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureSync(joinPoint, SyncSource.WHOSCORED, SyncType.FULL);
    }

    @Around(STATS_SYNC_LEAGUE)
    public Object measureWhoScoredLeagueSync(ProceedingJoinPoint joinPoint) throws Throwable {
        return measureSync(joinPoint, SyncSource.WHOSCORED, SyncType.SINGLE_LEAGUE);
    }

    /**
     * Un rechazo del dominio (409, 404 o 403) es {@code rejected}; se clasifica por la base de
     * {@code shared/}, así una excepción nueva del mercado queda cubierta. Un error interno no cuenta.
     */
    private Object measureOrder(ProceedingJoinPoint joinPoint, OrderOperation operation) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            recordSafely(BusinessMetrics.ORDERS, () -> metrics.recordOrder(operation, OrderResult.SUCCESS));
            return result;
        } catch (ConflictException | NotFoundException | ForbiddenException e) {
            recordSafely(BusinessMetrics.ORDERS, () -> metrics.recordOrder(operation, OrderResult.REJECTED));
            throw e;
        }
    }

    /**
     * Sin token de Football-Data la sincronización no se intentó: no registra nada.
     */
    private Object measureSync(ProceedingJoinPoint joinPoint, SyncSource source, SyncType type) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            recordSync(source, type, succeeded(result) ? SyncResult.SUCCESS : SyncResult.FAILURE, start);
            return result;
        } catch (SyncInProgressException e) {
            recordSync(source, type, SyncResult.IN_PROGRESS, start);
            throw e;
        } catch (RuntimeException e) {
            if (!(e instanceof SyncDisabledException)) {
                recordSync(source, type, SyncResult.FAILURE, start);
            }
            throw e;
        }
    }

    private void recordRecalculation(ProceedingJoinPoint joinPoint, RecalculationResult result, long start) {
        long elapsed = System.nanoTime() - start;
        recordSafely(BusinessMetrics.RECALCULATION, () -> metrics.recordRecalculation(
                RecalculationTrigger.from((RecalculationOrigin) joinPoint.getArgs()[0]), result, elapsed));
    }

    private void recordSync(SyncSource source, SyncType type, SyncResult result, long start) {
        long elapsed = System.nanoTime() - start;
        recordSafely(BusinessMetrics.SYNC, () -> metrics.recordSync(source, type, result, elapsed));
    }

    private static RecalculationResult outcomeOf(Object result) {
        return result instanceof RecalculationReport report && report.wasInterrupted()
                ? RecalculationResult.FAILURE
                : RecalculationResult.SUCCESS;
    }

    private static boolean succeeded(Object result) {
        if (result instanceof SyncReport report) {
            return report.succeeded();
        }
        return result instanceof StatsSyncReport report && report.succeeded();
    }

    /**
     * Una falla al registrar nunca llega al caso de uso: se loguea y se sigue.
     */
    private static void recordSafely(String metric, Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException e) {
            LOGGER.warn("No se pudo registrar la métrica {}.", metric, e);
        }
    }
}
