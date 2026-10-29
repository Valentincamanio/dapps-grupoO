package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.purchase;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.sale;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * La reconstrucción de las posiciones a una fecha (data-model 2.12, research D20), con el ejemplo
 * de US7.1 y US7.2: compras de 10 el 20/08 y de 5 el 26/08.
 */
class TradeReplayTest {
    private static final long USER = 20L;
    private static final long OTHER = 30L;
    private static final long KANE = 42L;
    private static final long MUSIALA = 43L;

    private static final Trade FIRST_PURCHASE = purchase(1L, USER, KANE, 10, "1.0000", at("2026-08-20T12:00"));
    private static final Trade SECOND_PURCHASE = sale(2L, OTHER, USER, KANE, 5, "1.0300", at("2026-08-26T12:00"));

    @Test
    void al25DeAgostoTieneLosDiezTokensDeLaPrimeraCompraUS71() {
        TradeReplay replay = TradeReplay.ofUser(USER, List.of(FIRST_PURCHASE));

        assertThat(replay.playerIds()).containsExactly(KANE);
        assertThat(replay.tokensOf(KANE)).isEqualTo(10);
        assertThat(replay.basisOf(KANE)).hasValueSatisfying(basis -> {
            assertThat(basis.averagePrice()).isEqualByComparingTo("1.0000");
            assertThat(basis.invested()).isEqualByComparingTo("10.00");
        });
        assertThat(replay.realizedProfit()).isEqualByComparingTo("0.00");
        assertThat(replay.withCost()).isTrue();
    }

    @Test
    void alPrimeroDeSeptiembreSumaLaSegundaCompraYPromediaLoPagadoUS72() {
        TradeReplay replay = TradeReplay.ofUser(USER, List.of(FIRST_PURCHASE, SECOND_PURCHASE));

        assertThat(replay.tokensOf(KANE)).isEqualTo(15);
        assertThat(replay.basisOf(KANE)).hasValueSatisfying(basis -> {
            assertThat(basis.averagePrice()).isEqualByComparingTo("1.0100");
            assertThat(basis.invested()).isEqualByComparingTo("15.15");
        });
    }

    @Test
    void reproduceEnOrdenDeEjecucionAunqueLleguenDesordenadas() {
        Trade earlySale = sale(3L, USER, OTHER, KANE, 4, "1.0500", at("2026-08-21T12:00"));

        TradeReplay replay = TradeReplay.ofUser(USER, List.of(earlySale, FIRST_PURCHASE));

        assertThat(replay.tokensOf(KANE)).isEqualTo(6);
        assertThat(replay.realizedProfit()).isEqualByComparingTo("0.20");
    }

    @Test
    void aIgualInstanteDesempataPorId() {
        Trade sameInstantSale = sale(5L, USER, OTHER, KANE, 10, "1.0000", at("2026-08-20T12:00"));

        TradeReplay replay = TradeReplay.ofUser(USER, List.of(sameInstantSale, FIRST_PURCHASE));

        assertThat(replay.playerIds()).isEmpty();
    }

    @Test
    void laRealizadaIncluyeSoloLasVentasQueRecibeLasAnterioresUS74() {
        Trade before = sale(3L, USER, OTHER, KANE, 4, "1.0500", at("2026-08-28T12:00"));
        Trade after = sale(4L, USER, OTHER, KANE, 2, "1.1000", at("2026-09-03T12:00"));

        TradeReplay untilSeptemberFirst = TradeReplay.ofUser(USER, List.of(FIRST_PURCHASE, before));
        TradeReplay untilToday = TradeReplay.ofUser(USER, List.of(FIRST_PURCHASE, before, after));

        assertThat(untilSeptemberFirst.realizedProfit()).isEqualByComparingTo("0.20");
        assertThat(untilSeptemberFirst.tokensOf(KANE)).isEqualTo(6);
        assertThat(untilToday.realizedProfit()).isEqualByComparingTo("0.40");
        assertThat(untilToday.tokensOf(KANE)).isEqualTo(4);
    }

    @Test
    void unJugadorQueQuedoEnCeroNoFigura() {
        Trade musiala = purchase(6L, USER, MUSIALA, 5, "1.0000", at("2026-08-21T12:00"));
        Trade soldOut = sale(7L, USER, OTHER, MUSIALA, 5, "0.9800", at("2026-08-22T12:00"));

        TradeReplay replay = TradeReplay.ofUser(USER, List.of(FIRST_PURCHASE, musiala, soldOut));

        assertThat(replay.playerIds()).containsExactly(KANE);
        assertThat(replay.tokensOf(MUSIALA)).isZero();
        assertThat(replay.basisOf(MUSIALA)).isEmpty();
        assertThat(replay.realizedProfit()).isEqualByComparingTo("-0.10");
    }

    @Test
    void sinOperacionesNoHayPosicionesNiRealizada() {
        TradeReplay replay = TradeReplay.ofUser(USER, List.of());

        assertThat(replay.playerIds()).isEmpty();
        assertThat(replay.realizedProfit()).isEqualByComparingTo("0.00");
    }

    @Test
    void elSuperusuarioTieneCienPorInicialMenosLoVendidoSinCosto() {
        List<MarketOrder> initialOrders = List.of(
                initialOrder(100L, KANE, 100, at("2026-08-17T00:00")),
                initialOrder(101L, MUSIALA, 100, at("2026-08-17T00:00")));
        Trade soldMusiala = purchase(8L, OTHER, MUSIALA, 100, "1.0000", at("2026-08-22T12:00"));

        TradeReplay replay = TradeReplay.ofSuperuser(initialOrders, List.of(FIRST_PURCHASE, SECOND_PURCHASE,
                soldMusiala).stream().filter(trade -> trade.sellerType() == OwnerType.ADMIN).toList());

        assertThat(replay.playerIds()).containsExactly(KANE);
        assertThat(replay.tokensOf(KANE)).isEqualTo(90);
        assertThat(replay.basisOf(KANE)).isEmpty();
        assertThat(replay.withCost()).isFalse();
        assertThat(replay.realizedProfit()).isNull();
    }

    @Test
    void conOperacionesAnterioresElSaldoEsElPosteriorDeLaUltima() {
        Trade lastBefore = sale(3L, USER, OTHER, KANE, 4, "1.0500", at("2026-08-28T12:00"));

        assertThat(TradeReplay.balanceAt(USER, FIRST_PURCHASE, null, new BigDecimal("1.00")))
                .isEqualByComparingTo("990.00");
        assertThat(TradeReplay.balanceAt(USER, lastBefore, SECOND_PURCHASE, new BigDecimal("1.00")))
                .isEqualByComparingTo("1004.20");
    }

    @Test
    void sinAnterioresElSaldoEsElPrevioDeLaPrimeraPosterior() {
        Trade firstAfterAsSeller = sale(3L, USER, OTHER, KANE, 4, "1.0500", at("2026-08-28T12:00"));

        assertThat(TradeReplay.balanceAt(USER, null, FIRST_PURCHASE, new BigDecimal("1.00")))
                .isEqualByComparingTo("1000.00");
        assertThat(TradeReplay.balanceAt(USER, null, firstAfterAsSeller, new BigDecimal("1.00")))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void sinNingunaOperacionElSaldoEsElActual() {
        assertThat(TradeReplay.balanceAt(USER, null, null, new BigDecimal("1234.56"))).isEqualByComparingTo("1234.56");
    }
}
