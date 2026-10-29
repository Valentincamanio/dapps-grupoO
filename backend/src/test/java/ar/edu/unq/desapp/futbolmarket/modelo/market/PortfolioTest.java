package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.activePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.costBasis;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.holding;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.inactivePlayer;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.initialOrder;
import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

class PortfolioTest {
    private static final long USER_ID = 7L;
    private static final Instant QUOTED_AT = at("2026-10-26T00:00");
    private static final Player KANE = activePlayer(12L, "Harry Kane");
    private static final Player SALAH = activePlayer(13L, "Mohamed Salah");
    private static final Player AARONSON = activePlayer(14L, "Brenden Aaronson");

    @Test
    void diezTokensCompradosA1ConCotizacion103GananTresPorCiento() {
        AppUser account = MarketFixtures.user(USER_ID, "990.00");
        Quote quote = quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT);

        Portfolio portfolio = Portfolio.current(account, List.of(view(KANE, costBasis(10, "10.00"), 0)),
                Map.of(KANE.id(), quote), MarketMath.ZERO_CREDITS);

        assertThat(portfolio.userId()).isEqualTo(USER_ID);
        assertThat(portfolio.date()).isNull();
        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.player()).isEqualTo(KANE);
            assertThat(position.tokens()).isEqualTo(10);
            assertThat(position.sharePercent()).isEqualTo(10);
            assertThat(position.committed()).isZero();
            assertThat(position.averagePrice()).isEqualTo(new BigDecimal("1.0000"));
            assertThat(position.invested()).isEqualTo(new BigDecimal("10.00"));
            assertThat(position.quote().tokenPrice()).isEqualTo(new BigDecimal("1.0300"));
            assertThat(position.quote().score()).isEqualTo(new BigDecimal("0.300000"));
            assertThat(position.value()).isEqualTo(new BigDecimal("10.30"));
            assertThat(position.profit()).isEqualTo(new BigDecimal("0.30"));
            assertThat(position.profitPercent()).isEqualTo(new BigDecimal("3.00"));
        });
    }

    @Test
    void losComprometidosSonLosDeSusVentasPendientes() {
        AppUser account = MarketFixtures.user(USER_ID, "1000.00");

        Portfolio portfolio = Portfolio.current(account, List.of(view(KANE, costBasis(10, "10.00"), 4)),
                Map.of(KANE.id(), quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT)), MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).singleElement()
                .satisfies(position -> assertThat(position.committed()).isEqualTo(4));
    }

    @Test
    void elTotalSumaLibreReservadoYElValorDeLasPosicionesEnElPatrimonio() {
        AppUser account = MarketFixtures.user(USER_ID, "996.18", "6.18");
        Map<Long, Quote> quotes = Map.of(
                KANE.id(), quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT),
                SALAH.id(), quote(2L, SALAH.id(), "103.00", "0.300000", QUOTED_AT));

        Portfolio portfolio = Portfolio.current(account, List.of(
                view(KANE, costBasis(10, "10.00"), 0),
                view(SALAH, costBasis(10, "10.00"), 0)), quotes, new BigDecimal("0.20"));

        PortfolioTotals totals = portfolio.totals();
        assertThat(totals.availableBalance()).isEqualByComparingTo("990.00");
        assertThat(totals.reservedBalance()).isEqualByComparingTo("6.18");
        assertThat(totals.balance()).isEqualByComparingTo("996.18");
        assertThat(totals.invested()).isEqualTo(new BigDecimal("20.00"));
        assertThat(totals.value()).isEqualTo(new BigDecimal("20.60"));
        assertThat(totals.profit()).isEqualTo(new BigDecimal("0.60"));
        assertThat(totals.realizedProfit()).isEqualTo(new BigDecimal("0.20"));
        assertThat(totals.equity()).isEqualByComparingTo("1016.78");
    }

    @Test
    void sinTenenciasNoHayPosicionesYElPatrimonioEsElSaldo() {
        AppUser account = MarketFixtures.user(USER_ID, "1000.00");

        Portfolio portfolio = Portfolio.current(account, List.of(), Map.of(), MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.totals().invested()).isEqualTo(MarketMath.ZERO_CREDITS);
        assertThat(portfolio.totals().value()).isEqualTo(MarketMath.ZERO_CREDITS);
        assertThat(portfolio.totals().profit()).isEqualTo(MarketMath.ZERO_CREDITS);
        assertThat(portfolio.totals().equity()).isEqualByComparingTo("1000.00");
    }

    @ParameterizedTest(name = "cotización {0} → valor {1}, ganancia {2}, rentabilidad {3} %")
    @CsvSource({
            "100.00, 10.00,  0.00,   0.00",
            "120.00, 12.00,  2.00,  20.00",
            "90.00,   9.00, -1.00, -10.00"})
    void diezTokensSeValuanConLaCotizacionVigente(String value, String positionValue, String profit,
                                                   String profitPercent) {
        AppUser account = MarketFixtures.user(USER_ID, "990.00");

        Portfolio portfolio = Portfolio.current(account, List.of(view(KANE, costBasis(10, "10.00"), 0)),
                Map.of(KANE.id(), quote(1L, KANE.id(), value, "0.000000", QUOTED_AT)), MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.value()).isEqualTo(new BigDecimal(positionValue));
            assertThat(position.profit()).isEqualTo(new BigDecimal(profit));
            assertThat(position.profitPercent()).isEqualTo(new BigDecimal(profitPercent));
        });
    }

    @Test
    void unJugadorInactivoSeValuaConSuUltimaCotizacion() {
        Player inactive = inactivePlayer(15L, "Thomas Müller");
        AppUser account = MarketFixtures.user(USER_ID, "990.00");

        Portfolio portfolio = Portfolio.current(account, List.of(view(inactive, costBasis(10, "10.00"), 0)),
                Map.of(inactive.id(), quote(1L, inactive.id(), "95.00", "-0.100000", QUOTED_AT)),
                MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.player().active()).isFalse();
            assertThat(position.value()).isEqualTo(new BigDecimal("9.50"));
        });
    }

    @Test
    void lasPosicionesVanPorNombreDelJugadorYDespuesPorId() {
        Player otherKane = activePlayer(11L, "Harry Kane");
        AppUser account = MarketFixtures.user(USER_ID, "1000.00");
        Map<Long, Quote> quotes = Map.of(
                KANE.id(), quote(1L, KANE.id(), "100.00", "0.000000", QUOTED_AT),
                SALAH.id(), quote(2L, SALAH.id(), "100.00", "0.000000", QUOTED_AT),
                AARONSON.id(), quote(3L, AARONSON.id(), "100.00", "0.000000", QUOTED_AT),
                otherKane.id(), quote(4L, otherKane.id(), "100.00", "0.000000", QUOTED_AT));

        Portfolio portfolio = Portfolio.current(account, List.of(
                view(SALAH, costBasis(1, "1.00"), 0),
                view(KANE, costBasis(1, "1.00"), 0),
                view(AARONSON, costBasis(1, "1.00"), 0),
                view(otherKane, costBasis(1, "1.00"), 0)), quotes, MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).extracting(position -> position.player().id())
                .containsExactly(AARONSON.id(), otherKane.id(), KANE.id(), SALAH.id());
    }

    @Test
    void unaTenenciaVaciaNoEsUnaPosicion() {
        AppUser account = MarketFixtures.user(USER_ID, "1000.00");

        Portfolio portfolio = Portfolio.current(account, List.of(view(KANE, CostBasis.EMPTY, 0)), Map.of(),
                MarketMath.ZERO_CREDITS);

        assertThat(portfolio.positions()).isEmpty();
    }

    @Test
    void unaTenenciaSinCotizacionEsUnErrorDeLosDatos() {
        AppUser account = MarketFixtures.user(USER_ID, "1000.00");
        List<HoldingView> holdings = List.of(view(KANE, costBasis(10, "10.00"), 0));
        Map<Long, Quote> noQuotes = Map.of();

        assertThatThrownBy(() -> Portfolio.current(account, holdings, noQuotes, MarketMath.ZERO_CREDITS))
                .isInstanceOf(MarketInvariantException.class)
                .hasMessage("La tenencia del jugador " + KANE.id() + " no tiene cotización.");
    }

    @Test
    void elDelSuperusuarioSonSusTokensSinVenderSinDatosDeCosto() {
        AppUser superuser = MarketFixtures.superuser("10.00");
        Quote quote = quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT);

        Portfolio portfolio = Portfolio.currentOfSuperuser(superuser,
                List.of(new OrderView(initialOrder(1L, KANE.id(), 90, QUOTED_AT), KANE)), Map.of(KANE.id(), quote));

        assertThat(portfolio.userId()).isEqualTo(MarketFixtures.SUPERUSER_ID);
        assertThat(portfolio.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(90);
            assertThat(position.sharePercent()).isEqualTo(90);
            assertThat(position.committed()).isEqualTo(90);
            assertThat(position.value()).isEqualTo(new BigDecimal("92.70"));
            assertThat(position.averagePrice()).isNull();
            assertThat(position.invested()).isNull();
            assertThat(position.profit()).isNull();
            assertThat(position.profitPercent()).isNull();
        });
        PortfolioTotals totals = portfolio.totals();
        assertThat(totals.balance()).isEqualByComparingTo("10.00");
        assertThat(totals.invested()).isEqualTo(MarketMath.ZERO_CREDITS);
        assertThat(totals.profit()).isEqualTo(MarketMath.ZERO_CREDITS);
        assertThat(totals.realizedProfit()).isNull();
        assertThat(totals.equity()).isEqualByComparingTo("102.70");
    }

    @Test
    void alSuperusuarioNoLeFiguranLasInicialesAgotadasNiLasDeJugadoresSinCotizacion() {
        AppUser superuser = MarketFixtures.superuser("100.00");

        Portfolio portfolio = Portfolio.currentOfSuperuser(superuser, List.of(
                        new OrderView(initialOrder(1L, KANE.id(), 0, QUOTED_AT), KANE),
                        new OrderView(initialOrder(2L, SALAH.id(), 100, QUOTED_AT), SALAH)),
                Map.of(KANE.id(), quote(1L, KANE.id(), "103.00", "0.300000", QUOTED_AT)));

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.totals().equity()).isEqualByComparingTo("100.00");
    }

    private static HoldingView view(Player player, CostBasis basis, int committed) {
        return new HoldingView(holding(player.id(), USER_ID, player.id(), basis, committed), player);
    }
}
