package ar.edu.unq.desapp.futbolmarket.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Las tres métricas de operaciones ejecutadas (research R8 y R11 de la 013; data-model §2.1): seis
 * series en cero desde el arranque, la etiqueta {@code counterparty} como única etiqueta, los
 * incrementos por operación con los centavos, y el catálogo de la 012 sin cambios.
 */
class BusinessMetricsTradesTest {

    private static final List<String> TRADE_NAMES = List.of(BusinessMetrics.TRADES, BusinessMetrics.TOKENS_TRADED,
            BusinessMetrics.CREDITS_TRADED);
    private static final List<String> NAMES_012 = List.of(BusinessMetrics.ORDERS, BusinessMetrics.RECALCULATION,
            BusinessMetrics.SYNC, BusinessMetrics.LOGINS, BusinessMetrics.REGISTRATIONS);
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            BusinessMetrics.TRADES, "Operaciones ejecutadas en el mercado: cada cruce confirmado entre una compra y "
                    + "una venta.",
            BusinessMetrics.TOKENS_TRADED, "Tokens que cambiaron de mano en las operaciones ejecutadas.",
            BusinessMetrics.CREDITS_TRADED, "Créditos pagados en las operaciones ejecutadas.");
    private static final String INITIAL_ORDER = "initial_order";
    private static final String USER = "user";
    private static final double CENT = 0.005;

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void bind() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics();
        metrics.bindTo(registry);
    }

    @Test
    void lasSeisSeriesExistenEnCeroDesdeElArranqueConSuDescripcion() {
        List<Meter> meters = tradeMeters();

        assertThat(meters).hasSize(6);
        assertThat(TRADE_NAMES).allSatisfy(name -> {
            assertThat(registry.find(name).counters()).hasSize(2)
                    .allSatisfy(counter -> assertThat(counter.count()).isZero());
            assertThat(registry.find(name).counters())
                    .allSatisfy(counter -> assertThat(counter.getId().getDescription()).isEqualTo(DESCRIPTIONS.get(name)));
        });
    }

    @Test
    void unaOperacionContraLaOrdenInicialSumaEnInitialOrderYNoEnUser() {
        metrics.recordTrade(OwnerType.ADMIN, 3, new BigDecimal("309.00"));

        assertThat(count(BusinessMetrics.TRADES, INITIAL_ORDER)).isEqualTo(1);
        assertThat(count(BusinessMetrics.TOKENS_TRADED, INITIAL_ORDER)).isEqualTo(3);
        assertThat(count(BusinessMetrics.CREDITS_TRADED, INITIAL_ORDER)).isCloseTo(309.00, within(CENT));
        assertThat(TRADE_NAMES).allSatisfy(name -> assertThat(count(name, USER)).isZero());
    }

    @Test
    void variasOperacionesAcumulanYConservanLosCentavos() {
        metrics.recordTrade(OwnerType.USER, 1, new BigDecimal("10.25"));
        metrics.recordTrade(OwnerType.USER, 2, new BigDecimal("0.10"));

        assertThat(count(BusinessMetrics.TRADES, USER)).isEqualTo(2);
        assertThat(count(BusinessMetrics.TOKENS_TRADED, USER)).isEqualTo(3);
        assertThat(count(BusinessMetrics.CREDITS_TRADED, USER)).isCloseTo(10.35, within(CENT));
    }

    @Test
    void laUnicaEtiquetaDeLasSeriesNuevasEsLaContraparteConSusDosValores() {
        List<Tag> tags = tradeMeters().stream().flatMap(meter -> meter.getId().getTags().stream()).toList();

        assertThat(tags).allSatisfy(tag -> {
            assertThat(tag.getKey()).isEqualTo(BusinessMetrics.COUNTERPARTY_TAG);
            assertThat(tag.getValue()).isIn(INITIAL_ORDER, USER);
        });
    }

    @Test
    void lasSeriesDeLa012SiguenSiendoLasMismas() {
        List<Meter> meters = registry.getMeters().stream()
                .filter(meter -> NAMES_012.contains(meter.getId().getName()))
                .toList();

        assertThat(meters).hasSize(30);
        assertThat(registry.getMeters()).hasSize(36);
    }

    private List<Meter> tradeMeters() {
        return registry.getMeters().stream().filter(meter -> TRADE_NAMES.contains(meter.getId().getName())).toList();
    }

    private double count(String name, String counterparty) {
        return registry.get(name).tag(BusinessMetrics.COUNTERPARTY_TAG, counterparty).counter().count();
    }
}
