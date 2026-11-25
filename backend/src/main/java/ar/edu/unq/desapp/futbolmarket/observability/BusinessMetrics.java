package ar.edu.unq.desapp.futbolmarket.observability;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * El catálogo de las métricas propias de negocio (research D5 y data-model §2 de la 012): nombres,
 * descripciones y etiquetas de los contadores y timers {@code futbolmarket.*}, y un método de
 * registro por caso de uso.
 *
 * <p>Al enlazarse con el registro da de alta <b>todas</b> las combinaciones de etiquetas en cero
 * (6 + 9 + 12 + 2 + 1 = 30 series de la 012, más 3 × 2 = 6 de las operaciones ejecutadas de la 013:
 * 36 series), así Prometheus las ve desde el primer scrape aunque todavía no haya pasado nada
 * (FR-017). Los valores de las etiquetas salen siempre de enums cerrados (FR-019 de la 012 y FR-020
 * de la 013). Los timers publican cantidad, suma y máximo, sin histograma ni percentiles.</p>
 *
 * <p>Las operaciones ejecutadas (research R8 de la 013) se cuentan en tres contadores con la
 * etiqueta {@code counterparty} ({@link TradeCounterparty}): la cantidad, los tokens y los créditos.
 * No llevan {@code baseUnit}, que Micrometer agregaría al nombre. Los créditos llegan como
 * {@code BigDecimal} de escala 2 y se suman como {@code double} (research R11): conserva los
 * centavos mientras el total quede por debajo de unos 10<sup>13</sup> créditos, y Prometheus igual
 * guarda float64. La fuente exacta sigue siendo la tabla de operaciones.</p>
 *
 * <p>Los gauges no están acá: leen la base y por eso viven en la capa de servicio (research D7).</p>
 */
@Component
public class BusinessMetrics implements MeterBinder {

    public static final String ORDERS = "futbolmarket.market.orders";
    public static final String RECALCULATION = "futbolmarket.quotes.recalculation";
    public static final String SYNC = "futbolmarket.sync";
    public static final String LOGINS = "futbolmarket.auth.logins";
    public static final String REGISTRATIONS = "futbolmarket.auth.registrations";
    public static final String TRADES = "futbolmarket.market.trades";
    public static final String TOKENS_TRADED = "futbolmarket.market.tokens.traded";
    public static final String CREDITS_TRADED = "futbolmarket.market.credits.traded";

    public static final String OPERATION_TAG = "operation";
    public static final String RESULT_TAG = "result";
    public static final String TRIGGER_TAG = "trigger";
    public static final String SOURCE_TAG = "source";
    public static final String TYPE_TAG = "type";
    public static final String COUNTERPARTY_TAG = "counterparty";

    private static final String ORDERS_DESCRIPTION = "Órdenes de compra, venta y cancelación pedidas por usuarios, "
            + "aceptadas o rechazadas por el mercado.";
    private static final String RECALCULATION_DESCRIPTION = "Duración de cada recálculo de cotizaciones, según qué "
            + "lo disparó y cómo terminó.";
    private static final String SYNC_DESCRIPTION = "Duración de cada sincronización con una fuente externa, completa "
            + "o de una liga, y cómo terminó.";
    private static final String LOGINS_DESCRIPTION = "Intentos de inicio de sesión, correctos o fallidos.";
    private static final String REGISTRATIONS_DESCRIPTION = "Cuentas registradas con éxito.";
    private static final String TRADES_DESCRIPTION = "Operaciones ejecutadas en el mercado: cada cruce confirmado "
            + "entre una compra y una venta.";
    private static final String TOKENS_TRADED_DESCRIPTION = "Tokens que cambiaron de mano en las operaciones "
            + "ejecutadas.";
    private static final String CREDITS_TRADED_DESCRIPTION = "Créditos pagados en las operaciones ejecutadas.";

    private MeterRegistry registry;

    /**
     * Guarda el registro y da de alta las 36 series en cero.
     */
    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        this.registry = meterRegistry;
        for (OrderOperation operation : OrderOperation.values()) {
            for (OrderResult result : OrderResult.values()) {
                orderCounter(operation, result);
            }
        }
        for (RecalculationTrigger trigger : RecalculationTrigger.values()) {
            for (RecalculationResult result : RecalculationResult.values()) {
                recalculationTimer(trigger, result);
            }
        }
        for (SyncSource source : SyncSource.values()) {
            for (SyncType type : SyncType.values()) {
                for (SyncResult result : SyncResult.values()) {
                    syncTimer(source, type, result);
                }
            }
        }
        for (LoginResult result : LoginResult.values()) {
            loginCounter(result);
        }
        registrationCounter();
        for (TradeCounterparty counterparty : TradeCounterparty.values()) {
            tradeCounter(TRADES, TRADES_DESCRIPTION, counterparty);
            tradeCounter(TOKENS_TRADED, TOKENS_TRADED_DESCRIPTION, counterparty);
            tradeCounter(CREDITS_TRADED, CREDITS_TRADED_DESCRIPTION, counterparty);
        }
    }

    public void recordOrder(OrderOperation operation, OrderResult result) {
        orderCounter(operation, result).increment();
    }

    public void recordLogin(LoginResult result) {
        loginCounter(result).increment();
    }

    public void recordRegistration() {
        registrationCounter().increment();
    }

    /**
     * Una operación ejecutada y confirmada, en la serie de su contraparte: suma 1 operación, sus
     * tokens y sus créditos.
     */
    public void recordTrade(OwnerType sellerType, int quantity, BigDecimal total) {
        TradeCounterparty counterparty = TradeCounterparty.from(sellerType);
        tradeCounter(TRADES, TRADES_DESCRIPTION, counterparty).increment();
        tradeCounter(TOKENS_TRADED, TOKENS_TRADED_DESCRIPTION, counterparty).increment(quantity);
        tradeCounter(CREDITS_TRADED, CREDITS_TRADED_DESCRIPTION, counterparty).increment(total.doubleValue());
    }

    public void recordRecalculation(RecalculationTrigger trigger, RecalculationResult result, long nanos) {
        recalculationTimer(trigger, result).record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordSync(SyncSource source, SyncType type, SyncResult result, long nanos) {
        syncTimer(source, type, result).record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Los valores de {@code source} y {@code type} son los enums del modelo en minúsculas
     * ({@code football_data}, {@code single_league}).
     */
    private static String tagOf(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private Counter orderCounter(OrderOperation operation, OrderResult result) {
        return Counter.builder(ORDERS)
                .description(ORDERS_DESCRIPTION)
                .tags(Tags.of(OPERATION_TAG, operation.tag(), RESULT_TAG, result.tag()))
                .register(registry);
    }

    private Counter loginCounter(LoginResult result) {
        return Counter.builder(LOGINS)
                .description(LOGINS_DESCRIPTION)
                .tag(RESULT_TAG, result.tag())
                .register(registry);
    }

    private Counter registrationCounter() {
        return Counter.builder(REGISTRATIONS)
                .description(REGISTRATIONS_DESCRIPTION)
                .register(registry);
    }

    private Counter tradeCounter(String name, String description, TradeCounterparty counterparty) {
        return Counter.builder(name)
                .description(description)
                .tag(COUNTERPARTY_TAG, counterparty.tag())
                .register(registry);
    }

    private Timer recalculationTimer(RecalculationTrigger trigger, RecalculationResult result) {
        return Timer.builder(RECALCULATION)
                .description(RECALCULATION_DESCRIPTION)
                .tags(Tags.of(TRIGGER_TAG, trigger.tag(), RESULT_TAG, result.tag()))
                .register(registry);
    }

    private Timer syncTimer(SyncSource source, SyncType type, SyncResult result) {
        return Timer.builder(SYNC)
                .description(SYNC_DESCRIPTION)
                .tags(Tags.of(SOURCE_TAG, tagOf(source), TYPE_TAG, tagOf(type), RESULT_TAG, result.tag()))
                .register(registry);
    }
}
