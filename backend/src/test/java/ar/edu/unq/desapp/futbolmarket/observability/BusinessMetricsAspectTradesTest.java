package ar.edu.unq.desapp.futbolmarket.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientCreditsException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Las operaciones ejecutadas en el aspecto de métricas (research R9 de la 013), con el join point
 * mockeado como en {@code BusinessMetricsAspectTest}: {@code buy} y {@code sell} registran cada
 * {@link Trade} del {@link BookOutcome} en la serie de su contraparte, un pedido que lanza no suma,
 * {@code cancel} nunca suma y una falla al registrar no cambia el resultado (FR-021).
 */
class BusinessMetricsAspectTradesTest {

    private static final long BUYER = 7;
    private static final long SELLER = 8;
    private static final long OTHER_SELLER = 9;
    private static final long PLAYER = 30;
    private static final Instant AT = Instant.parse("2026-10-28T15:00:00Z");
    private static final String INITIAL_ORDER = "initial_order";
    private static final String USER = "user";
    private static final double CENT = 0.005;

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
    void unaCompraQueQuedaPendienteNoSumaOperaciones() throws Throwable {
        BookOutcome outcome = outcomeWith();

        Object result = aspect.measureBuy(returning(outcome));

        assertThat(result).isSameAs(outcome);
        assertThat(allTrades()).isZero();
    }

    @Test
    void unaCompraQueCruzaConLaOrdenInicialSumaEnInitialOrder() throws Throwable {
        Trade purchase = MarketFixtures.purchase(1, BUYER, PLAYER, 3, "103.00", AT);

        aspect.measureBuy(returning(outcomeWith(purchase)));

        assertThat(count(BusinessMetrics.TRADES, INITIAL_ORDER)).isEqualTo(1);
        assertThat(count(BusinessMetrics.TOKENS_TRADED, INITIAL_ORDER)).isEqualTo(3);
        assertThat(count(BusinessMetrics.CREDITS_TRADED, INITIAL_ORDER)).isCloseTo(309.00, within(CENT));
        assertThat(count(BusinessMetrics.TRADES, USER)).isZero();
    }

    @Test
    void unaCompraQueCruzaConDosVentasDeUsuariosSumaLasDosEnUser() throws Throwable {
        Trade first = MarketFixtures.sale(1, SELLER, BUYER, PLAYER, 2, "103.50", AT);
        Trade second = MarketFixtures.sale(2, OTHER_SELLER, BUYER, PLAYER, 5, "104.25", AT);

        aspect.measureBuy(returning(outcomeWith(first, second)));

        assertThat(count(BusinessMetrics.TRADES, USER)).isEqualTo(2);
        assertThat(count(BusinessMetrics.TOKENS_TRADED, USER)).isEqualTo(7);
        assertThat(count(BusinessMetrics.CREDITS_TRADED, USER))
                .isCloseTo(first.total().add(second.total()).doubleValue(), within(CENT));
        assertThat(count(BusinessMetrics.TRADES, INITIAL_ORDER)).isZero();
    }

    @Test
    void unaVentaQueCruzaSumaEnLaSerieDelTipoDeVendedor() throws Throwable {
        Trade sale = MarketFixtures.sale(1, SELLER, BUYER, PLAYER, 4, "101.00", AT);

        aspect.measureSell(returning(outcomeWith(sale)));

        assertThat(count(BusinessMetrics.TRADES, USER)).isEqualTo(1);
        assertThat(count(BusinessMetrics.TOKENS_TRADED, USER)).isEqualTo(4);
        assertThat(count(BusinessMetrics.CREDITS_TRADED, USER)).isCloseTo(404.00, within(CENT));
    }

    @Test
    void unaCompraRechazadaRelanzaLaMismaExcepcionYSoloSumaElRechazoDeLa012() throws Throwable {
        RuntimeException rejected = new InsufficientCreditsException(BigDecimal.TEN, BigDecimal.ONE);

        Throwable thrown = catchThrowable(() -> aspect.measureBuy(throwing(rejected)));

        assertThat(thrown).isSameAs(rejected);
        assertThat(allTrades()).isZero();
        assertThat(registry.get(BusinessMetrics.ORDERS).tag(BusinessMetrics.OPERATION_TAG, "buy")
                .tag(BusinessMetrics.RESULT_TAG, "rejected").counter().count()).isEqualTo(1);
    }

    @Test
    void unaVentaQueFallaAlGrabarRelanzaLaMismaExcepcionYNoSuma() throws Throwable {
        RuntimeException failure = new MarketInvariantException("Se guardaron 1 elementos de 2.");

        Throwable thrown = catchThrowable(() -> aspect.measureSell(throwing(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(allTrades()).isZero();
    }

    @Test
    void cancelarNuncaSumaOperaciones() throws Throwable {
        BookOutcome outcome = outcomeWith(MarketFixtures.purchase(1, BUYER, PLAYER, 3, "103.00", AT));

        Object result = aspect.measureCancel(returning(outcome));

        assertThat(result).isSameAs(outcome);
        assertThat(allTrades()).isZero();
    }

    @Test
    void unRegistroDeOperacionesQueFallaDevuelveElMismoResultado() throws Throwable {
        BusinessMetrics broken = mock(BusinessMetrics.class);
        doThrow(new IllegalStateException("Registro roto.")).when(broken).recordTrade(any(), anyInt(), any());
        BusinessMetricsAspect brokenAspect = new BusinessMetricsAspect(broken);
        BookOutcome outcome = outcomeWith(MarketFixtures.purchase(1, BUYER, PLAYER, 3, "103.00", AT));

        Object bought = brokenAspect.measureBuy(returning(outcome));
        Object sold = brokenAspect.measureSell(returning(outcome));

        assertThat(bought).isSameAs(outcome);
        assertThat(sold).isSameAs(outcome);
    }

    private static BookOutcome outcomeWith(Trade... trades) {
        BookOutcome outcome = mock(BookOutcome.class);
        when(outcome.trades()).thenReturn(List.of(trades));
        return outcome;
    }

    private static ProceedingJoinPoint returning(Object value) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(value);
        return joinPoint;
    }

    private static ProceedingJoinPoint throwing(RuntimeException failure) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(failure);
        return joinPoint;
    }

    private double count(String name, String counterparty) {
        return registry.get(name).tag(BusinessMetrics.COUNTERPARTY_TAG, counterparty).counter().count();
    }

    private double allTrades() {
        return List.of(BusinessMetrics.TRADES, BusinessMetrics.TOKENS_TRADED, BusinessMetrics.CREDITS_TRADED).stream()
                .flatMap(name -> registry.get(name).counters().stream())
                .mapToDouble(counter -> counter.count())
                .sum();
    }
}
