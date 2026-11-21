package ar.edu.unq.desapp.futbolmarket.service.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.config.QuoteRecalculationProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationBlockedBySyncException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.RecalculationInProgressException;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncLock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * El timer {@code futbolmarket.quotes.recalculation} alrededor del recálculo real (US2.6 de la 012).
 * Los candados no son reentrantes, así que tomarlos en el mismo hilo reproduce el rechazo sin
 * concurrencia (research D12). Cada caso mide la diferencia antes/después.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:recalculation-metrics-it;DB_CLOSE_DELAY=-1")
class RecalculationMetricsIT {
    private static final String RECALCULATION = "futbolmarket.quotes.recalculation";
    private static final String TRIGGER = "trigger";
    private static final String RESULT = "result";
    private static final String MANUAL = "manual";
    private static final String STARTUP = "startup";
    private static final String SUCCESS = "success";
    private static final String BLOCKED = "blocked";
    private static final String DISABLED_CRON = "-";
    private static final String ZONE = "America/Argentina/Buenos_Aires";

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private RecalculationLock recalculationLock;

    @Autowired
    private SyncLock syncLock;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        database.clean();
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void unRecalculoManualQueTerminaBienSumaUnSuccessConSuDuracion() {
        long countBefore = timer(MANUAL, SUCCESS).count();
        double timeBefore = timer(MANUAL, SUCCESS).totalTime(TimeUnit.NANOSECONDS);

        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(timer(MANUAL, SUCCESS).count()).isEqualTo(countBefore + 1);
        assertThat(timer(MANUAL, SUCCESS).totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(timeBefore);
    }

    @Test
    void conOtroRecalculoEnCursoSumaUnBlocked() {
        long before = timer(MANUAL, BLOCKED).count();

        Throwable thrown = catchThrowable(() -> recalculationLock.runExclusively(
                () -> recalculationService.recalculate(RecalculationOrigin.MANUAL)));

        assertThat(thrown).isInstanceOf(RecalculationInProgressException.class);
        assertThat(timer(MANUAL, BLOCKED).count()).isEqualTo(before + 1);
    }

    @Test
    void conUnaSincronizacionEnCursoSumaUnBlocked() {
        long before = timer(MANUAL, BLOCKED).count();

        Throwable thrown = catchThrowable(() -> syncLock.runExclusively(
                () -> recalculationService.recalculate(RecalculationOrigin.MANUAL)));

        assertThat(thrown).isInstanceOf(RecalculationBlockedBySyncException.class);
        assertThat(timer(MANUAL, BLOCKED).count()).isEqualTo(before + 1);
    }

    @Test
    void elArranqueSinSemanasPendientesNoRegistraNada() {
        QuoteStartupRecalculation startup = new QuoteStartupRecalculation(
                new QuoteRecalculationProperties(DISABLED_CRON, ZONE, true), recalculationService);
        long before = startupCount();

        startup.recalculatePendingWeeks();

        assertThat(startupCount()).isEqualTo(before);
    }

    private Timer timer(String trigger, String result) {
        return registry.get(RECALCULATION).tag(TRIGGER, trigger).tag(RESULT, result).timer();
    }

    private long startupCount() {
        return registry.get(RECALCULATION).tag(TRIGGER, STARTUP).timers().stream().mapToLong(Timer::count).sum();
    }
}
