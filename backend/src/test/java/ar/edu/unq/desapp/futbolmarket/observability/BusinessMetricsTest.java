package ar.edu.unq.desapp.futbolmarket.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * El catálogo de métricas propias (data-model §1 y §2 de la 012): las 30 series en cero desde el
 * arranque, descripciones en español, etiquetas cerradas y un registro por caso.
 */
class BusinessMetricsTest {

    private static final List<String> NAMES = List.of(BusinessMetrics.ORDERS, BusinessMetrics.RECALCULATION,
            BusinessMetrics.SYNC, BusinessMetrics.LOGINS, BusinessMetrics.REGISTRATIONS);
    private static final Map<String, Set<String>> VOCABULARY = Map.of(
            BusinessMetrics.OPERATION_TAG, Set.of("buy", "sell", "cancel"),
            BusinessMetrics.RESULT_TAG, Set.of("success", "rejected", "failure", "blocked", "in_progress"),
            BusinessMetrics.TRIGGER_TAG, Set.of("manual", "scheduled", "startup"),
            BusinessMetrics.SOURCE_TAG, Set.of("football_data", "whoscored"),
            BusinessMetrics.TYPE_TAG, Set.of("full", "single_league"));
    private static final int TOTAL_SERIES = 30;
    private static final long ELAPSED_MILLIS = 250;

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void bind() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics();
        metrics.bindTo(registry);
    }

    @Test
    void lasTreintaSeriesExistenEnCeroDesdeElArranque() {
        List<Meter> meters = ownMeters();

        assertThat(meters).hasSize(TOTAL_SERIES);
        assertThat(registry.find(BusinessMetrics.ORDERS).counters()).hasSize(6)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(BusinessMetrics.RECALCULATION).timers()).hasSize(9)
                .allSatisfy(timer -> assertThat(timer.count()).isZero());
        assertThat(registry.find(BusinessMetrics.SYNC).timers()).hasSize(12)
                .allSatisfy(timer -> assertThat(timer.count()).isZero());
        assertThat(registry.find(BusinessMetrics.LOGINS).counters()).hasSize(2)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(BusinessMetrics.REGISTRATIONS).counters()).hasSize(1)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    void cadaMetricaTieneDescripcionEnEspanol() {
        List<Meter> meters = ownMeters();

        assertThat(meters).allSatisfy(meter -> assertThat(meter.getId().getDescription())
                .isNotBlank()
                .containsAnyOf("ó", "á", "é", "ú", "í"));
    }

    @Test
    void ningunaEtiquetaTieneUnValorFueraDelVocabulario() {
        List<Tag> tags = ownMeters().stream().flatMap(meter -> meter.getId().getTags().stream()).toList();

        assertThat(tags).allSatisfy(tag -> assertThat(VOCABULARY.get(tag.getKey())).contains(tag.getValue()));
    }

    @Test
    void unaCompraRechazadaSumaUnRejectedYNadaMas() {
        metrics.recordOrder(OrderOperation.BUY, OrderResult.REJECTED);

        assertThat(order("buy", "rejected").count()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void unLoginFallidoSumaUnFailureYNadaMas() {
        metrics.recordLogin(LoginResult.FAILURE);

        assertThat(registry.get(BusinessMetrics.LOGINS).tag(BusinessMetrics.RESULT_TAG, "failure").counter().count())
                .isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void unRegistroSumaUnoYNadaMas() {
        metrics.recordRegistration();

        assertThat(registry.get(BusinessMetrics.REGISTRATIONS).counter().count()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void unRecalculoProgramadoBloqueadoRegistraSuDuracion() {
        long nanos = Duration.ofMillis(ELAPSED_MILLIS).toNanos();

        metrics.recordRecalculation(RecalculationTrigger.SCHEDULED, RecalculationResult.BLOCKED, nanos);

        Timer timer = registry.get(BusinessMetrics.RECALCULATION)
                .tag(BusinessMetrics.TRIGGER_TAG, "scheduled").tag(BusinessMetrics.RESULT_TAG, "blocked").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(ELAPSED_MILLIS);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void unaSincronizacionDeUnaLigaEnCursoRegistraSuDuracion() {
        long nanos = Duration.ofMillis(ELAPSED_MILLIS).toNanos();

        metrics.recordSync(SyncSource.FOOTBALL_DATA, SyncType.SINGLE_LEAGUE, SyncResult.IN_PROGRESS, nanos);

        Timer timer = registry.get(BusinessMetrics.SYNC)
                .tag(BusinessMetrics.SOURCE_TAG, "football_data")
                .tag(BusinessMetrics.TYPE_TAG, "single_league")
                .tag(BusinessMetrics.RESULT_TAG, "in_progress").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(ELAPSED_MILLIS);
        assertThat(totalCount()).isEqualTo(1);
    }

    private Counter order(String operation, String result) {
        return registry.get(BusinessMetrics.ORDERS)
                .tag(BusinessMetrics.OPERATION_TAG, operation).tag(BusinessMetrics.RESULT_TAG, result).counter();
    }

    private List<Meter> ownMeters() {
        return registry.getMeters().stream().filter(meter -> NAMES.contains(meter.getId().getName())).toList();
    }

    private double totalCount() {
        return ownMeters().stream().mapToDouble(BusinessMetricsTest::countOf).sum();
    }

    private static double countOf(Meter meter) {
        if (meter instanceof Timer timer) {
            return timer.count();
        }
        return ((Counter) meter).count();
    }
}
