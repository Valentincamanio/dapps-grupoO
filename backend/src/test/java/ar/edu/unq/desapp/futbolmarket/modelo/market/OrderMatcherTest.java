package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
}
