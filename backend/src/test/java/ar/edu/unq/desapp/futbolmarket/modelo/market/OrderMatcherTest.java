package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class OrderMatcherTest {

    private static final long BUYER = 10L;
    private static final long USER_B = 11L;
    private static final long USER_D = 12L;
    private static final long PLAYER = 42L;
    private static final Instant MONDAY = at("2026-08-24T10:00");
    private static final Instant TUESDAY = at("2026-08-25T10:00");
    private static final BigDecimal PRICE = new BigDecimal("1.0300");
    private static final long SELLER = 20L;
    private static final long USER_A = 21L;
    private static final long USER_C = 22L;
    private static final BigDecimal RISEN_PRICE = new BigDecimal("1.1000");
    private static final Map<Long, BigDecimal> PLENTY = Map.of(USER_A, new BigDecimal("1000.00"),
            USER_C, new BigDecimal("1000.00"), SELLER, new BigDecimal("1000.00"));

    @Test
    void unaCompraDeDiezContraLaInicialConCienEsUnSoloCruceAUnPesoPorDiez() {
        MarketOrder initial = initialOrder(1L, PLAYER, 100, MONDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 10, Optional.of(initial), List.of(), PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.counterpart()).isSameAs(initial);
            assertThat(fill.quantity()).isEqualTo(10);
            assertThat(fill.tokenPrice()).isEqualByComparingTo("1.0000");
            assertThat(fill.total()).isEqualTo(new BigDecimal("10.00"));
        });
        assertThat(plan.remainder()).isZero();
        assertThat(plan.executedQuantity()).isEqualTo(10);
        assertThat(plan.executedTotal()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void conTresEnLaInicialSeEjecutanTresYQuedanSieteDeRemanente() {
        MarketOrder initial = initialOrder(1L, PLAYER, 3, MONDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 10, Optional.of(initial), List.of(), PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.quantity()).isEqualTo(3);
            assertThat(fill.total()).isEqualTo(new BigDecimal("3.00"));
        });
        assertThat(plan.remainder()).isEqualTo(7);
    }

    @Test
    void conLaInicialAgotadaSeCruzaConUnaVentaAlPrecioVigente() {
        MarketOrder initial = initialOrder(1L, PLAYER, 0, MONDAY);
        MarketOrder sale = sell(2L, USER_B, PLAYER, 5, MONDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 10, Optional.of(initial), List.of(sale), PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.counterpart()).isSameAs(sale);
            assertThat(fill.quantity()).isEqualTo(5);
            assertThat(fill.tokenPrice()).isEqualByComparingTo(PRICE);
            assertThat(fill.total()).isEqualTo(new BigDecimal("5.15"));
        });
        assertThat(plan.remainder()).isEqualTo(5);
    }

    @Test
    void laInicialSeUsaAntesQueLasVentasDeUsuarios() {
        MarketOrder initial = initialOrder(9L, PLAYER, 2, TUESDAY);
        MarketOrder sale = sell(2L, USER_B, PLAYER, 5, MONDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 4, Optional.of(initial), List.of(sale), PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(initial, sale);
        assertThat(plan.fills()).extracting(Fill::quantity).containsExactly(2, 2);
        assertThat(plan.fills()).extracting(Fill::total)
                .containsExactly(new BigDecimal("2.00"), new BigDecimal("2.06"));
        assertThat(plan.remainder()).isZero();
    }

    @Test
    void lasVentasSeCruzanDeLaMasViejaALaMasNuevaAunqueLleguenDesordenadas() {
        MarketOrder fromB = sell(2L, USER_B, PLAYER, 5, MONDAY);
        MarketOrder fromD = sell(3L, USER_D, PLAYER, 5, TUESDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 7, Optional.empty(), List.of(fromD, fromB), PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(fromB, fromD);
        assertThat(plan.fills()).extracting(Fill::quantity).containsExactly(5, 2);
        assertThat(plan.remainder()).isZero();
    }

    @Test
    void conElMismoInstanteDesempataElId() {
        MarketOrder older = sell(2L, USER_B, PLAYER, 5, MONDAY);
        MarketOrder newer = sell(3L, USER_D, PLAYER, 5, MONDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 5, Optional.empty(), List.of(newer, older), PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(older);
    }

    @Test
    void lasVentasDelMismoDuenoSeSalteanYLasComprasNoCuentan() {
        MarketOrder own = sell(2L, BUYER, PLAYER, 5, MONDAY);
        MarketOrder purchase = MarketFixtures.buy(3L, USER_B, PLAYER, 5, "1.0300", MONDAY);
        MarketOrder fromD = sell(4L, USER_D, PLAYER, 3, TUESDAY);

        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 5, Optional.empty(), List.of(own, purchase, fromD), PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(fromD);
        assertThat(plan.remainder()).isEqualTo(2);
    }

    @Test
    void sinNadaParaCruzarTodoEsRemanente() {
        MatchPlan plan = OrderMatcher.matchBuy(BUYER, 10, Optional.empty(), List.of(), PRICE);

        assertThat(plan.fills()).isEmpty();
        assertThat(plan.remainder()).isEqualTo(10);
        assertThat(plan.executedTotal()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void elPlanNoCambiaNingunaOrden() {
        MarketOrder initial = initialOrder(1L, PLAYER, 3, MONDAY);
        MarketOrder sale = sell(2L, USER_B, PLAYER, 5, MONDAY);

        OrderMatcher.matchBuy(BUYER, 10, Optional.of(initial), List.of(sale), PRICE);

        assertThat(initial.pending()).isEqualTo(3);
        assertThat(sale.pending()).isEqualTo(5);
    }

    @Test
    void unaVentaDeOchoContraUnaCompraDeCincoVendeCincoYQuedanTresDeRemanente() {
        MarketOrder purchase = MarketFixtures.buy(2L, USER_A, PLAYER, 5, "1.0300", MONDAY);

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 8, List.of(purchase), PLENTY, PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.counterpart()).isSameAs(purchase);
            assertThat(fill.quantity()).isEqualTo(5);
            assertThat(fill.tokenPrice()).isEqualByComparingTo(PRICE);
            assertThat(fill.total()).isEqualTo(new BigDecimal("5.15"));
        });
        assertThat(plan.remainder()).isEqualTo(3);
        assertThat(plan.shortfalls()).isEmpty();
    }

    @Test
    void conDosComprasCompletaLaMasViejaYSigueConLaOtra() {
        MarketOrder fromA = MarketFixtures.buy(2L, USER_A, PLAYER, 5, "1.0300", MONDAY);
        MarketOrder fromC = MarketFixtures.buy(3L, USER_C, PLAYER, 5, "1.0300", TUESDAY);

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 7, List.of(fromC, fromA), PLENTY, PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(fromA, fromC);
        assertThat(plan.fills()).extracting(Fill::quantity).containsExactly(5, 2);
        assertThat(plan.remainder()).isZero();
    }

    @Test
    void unaCompraConDiezTreintaDisponiblesAUnoDiezPagaNueveYElRestoEsFaltante() {
        MarketOrder purchase = MarketFixtures.buy(2L, USER_A, PLAYER, 10, "1.0300", MONDAY);

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 10, List.of(purchase), Map.of(USER_A, zero()), RISEN_PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.quantity()).isEqualTo(9);
            assertThat(fill.total()).isEqualTo(new BigDecimal("9.90"));
        });
        assertThat(plan.shortfalls()).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.order()).isSameAs(purchase);
            assertThat(shortfall.quantity()).isEqualTo(1);
        });
        assertThat(plan.remainder()).isEqualTo(1);
    }

    @Test
    void siAlcanzaParaLoQueSeVendeNoHayFaltanteAunqueNoAlcanceParaTodoElPendiente() {
        MarketOrder purchase = MarketFixtures.buy(2L, USER_A, PLAYER, 10, "1.0300", MONDAY);

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 4, List.of(purchase), Map.of(USER_A, zero()), RISEN_PRICE);

        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.quantity()).isEqualTo(4);
            assertThat(fill.total()).isEqualTo(new BigDecimal("4.40"));
        });
        assertThat(plan.shortfalls()).isEmpty();
        assertThat(plan.remainder()).isZero();
    }

    @Test
    void unaCompraQueNoPagaNiUnTokenEsFaltanteEnteraYLaVentaSigueConLaProxima() {
        MarketOrder poor = buyWithReservation(2L, USER_A, 5, "0.50", MONDAY);
        MarketOrder fromC = MarketFixtures.buy(3L, USER_C, PLAYER, 5, "1.1000", TUESDAY);
        Map<Long, BigDecimal> free = Map.of(USER_A, new BigDecimal("0.50"), USER_C, zero());

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 5, List.of(poor, fromC), free, RISEN_PRICE);

        assertThat(plan.shortfalls()).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.order()).isSameAs(poor);
            assertThat(shortfall.quantity()).isEqualTo(5);
        });
        assertThat(plan.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.counterpart()).isSameAs(fromC);
            assertThat(fill.quantity()).isEqualTo(5);
        });
        assertThat(plan.remainder()).isZero();
    }

    @Test
    void lasComprasDelMismoDuenoSeSalteanYLasVentasNoCuentan() {
        MarketOrder own = MarketFixtures.buy(2L, SELLER, PLAYER, 5, "1.0300", MONDAY);
        MarketOrder sale = sell(3L, USER_A, PLAYER, 5, MONDAY);
        MarketOrder fromC = MarketFixtures.buy(4L, USER_C, PLAYER, 3, "1.0300", TUESDAY);

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 5, List.of(own, sale, fromC), PLENTY, PRICE);

        assertThat(plan.fills()).extracting(Fill::counterpart).containsExactly(fromC);
        assertThat(plan.remainder()).isEqualTo(2);
    }

    @Test
    void elResultadoDeUnaVentaNoDependeDelOrdenDeEntrada() {
        MarketOrder fromA = MarketFixtures.buy(2L, USER_A, PLAYER, 4, "1.0300", MONDAY);
        MarketOrder fromC = MarketFixtures.buy(3L, USER_C, PLAYER, 4, "1.0300", MONDAY);
        MarketOrder late = MarketFixtures.buy(4L, USER_B, PLAYER, 4, "1.0300", TUESDAY);
        Map<Long, BigDecimal> free = Map.of(USER_A, zero(), USER_C, zero(), USER_B, zero());

        MatchPlan forward = OrderMatcher.matchSell(SELLER, 10, List.of(fromA, fromC, late), free, PRICE);
        MatchPlan backward = OrderMatcher.matchSell(SELLER, 10, List.of(late, fromC, fromA), free, PRICE);

        assertThat(backward).isEqualTo(forward);
        assertThat(forward.fills()).extracting(Fill::counterpart).containsExactly(fromA, fromC, late);
        assertThat(forward.fills()).extracting(Fill::quantity).containsExactly(4, 4, 2);
    }

    @Test
    void loQueUnaCompraTomaDelLibreYaNoEstaParaLaSiguienteDelMismoDueno() {
        MarketOrder first = MarketFixtures.buy(2L, USER_A, PLAYER, 5, "1.0000", MONDAY);
        MarketOrder second = MarketFixtures.buy(3L, USER_A, PLAYER, 5, "1.0000", TUESDAY);
        Map<Long, BigDecimal> free = Map.of(USER_A, new BigDecimal("0.50"));

        MatchPlan plan = OrderMatcher.matchSell(SELLER, 10, List.of(first, second), free, RISEN_PRICE);

        assertThat(plan.fills()).extracting(Fill::quantity).containsExactly(5, 4);
        assertThat(plan.shortfalls()).singleElement().satisfies(shortfall -> {
            assertThat(shortfall.order()).isSameAs(second);
            assertThat(shortfall.quantity()).isEqualTo(1);
        });
    }

    private static MarketOrder buyWithReservation(long id, long ownerId, int quantity, String reserved,
                                                  Instant createdAt) {
        return MarketOrder.reconstitute(new MarketOrder.State(id, OrderSide.BUY, ownerId, PLAYER, quantity, 0, 0,
                new BigDecimal(reserved), new BigDecimal("1.0300"), createdAt, false, null));
    }

    private static BigDecimal zero() {
        return MarketMath.ZERO_CREDITS;
    }
}
