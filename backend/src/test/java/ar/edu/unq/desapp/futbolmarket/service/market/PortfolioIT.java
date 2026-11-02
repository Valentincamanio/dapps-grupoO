package ar.edu.unq.desapp.futbolmarket.service.market;

import static ar.edu.unq.desapp.futbolmarket.modelo.market.MarketFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.Portfolio;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PortfolioPosition;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;

/**
 * El portfolio a una fecha de punta a punta del servicio contra una H2 propia (US7, research D20),
 * con el ejemplo del spec: Kane cotiza 100,00 desde el 17/08, 103,00 desde el 24/08 y 101,00 desde
 * el 31/08. Las operaciones pasan por {@link MarketOrderService} con el reloj en el momento de cada
 * una; el administrador se registra el 10/08, cuando arranca la aplicación.
 *
 * <p>Ana compra 10 a la orden inicial el 20/08, Bruno compra los 90 que quedan el 21/08, y el 26/08
 * Bruno le vende 5 a Ana a 1,0300. El 03/09 Bruno le vende otros 5 a 1,0100. Los pedidos se hacen
 * con el reloj en el 05/09.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PortfolioIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String INITIAL_BALANCE = "1000.00";
    private static final String SCORE_100 = "0.000000";
    private static final String SCORE_103 = "0.300000";
    private static final String SCORE_101 = "0.100000";
    private static final LocalDate AUGUST_25 = LocalDate.parse("2026-08-25");
    private static final LocalDate SEPTEMBER_1 = LocalDate.parse("2026-09-01");

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> "admin_" + SUFFIX);
        registry.add("futbolmarket.auth.admin.email", () -> "admin_" + SUFFIX + "@correo.com");
        registry.add("futbolmarket.auth.admin.password", () -> "Admin_" + UUID.randomUUID());
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:portfolio-it-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @TestConfiguration
    static class AugustClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(at("2026-08-10T09:00"));
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private InitialOrderService initialOrderService;

    @Autowired
    private MutableClock clock;

    private MarketDatabase database;
    private Player kane;
    private Quote august24;
    private AppUser ana;
    private AppUser bruno;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        database.quote(kane, "100.00", SCORE_100, at("2026-08-17T00:00"));
        august24 = database.quote(kane, "103.00", SCORE_103, at("2026-08-24T00:00"));
        database.quote(kane, "101.00", SCORE_101, at("2026-08-31T00:00"));
        clock.set(at("2026-08-17T12:00"));
        initialOrderService.openMissing();
        ana = database.user(INITIAL_BALANCE, at("2026-08-12T10:00"));
        bruno = database.user(INITIAL_BALANCE, at("2026-08-12T10:00"));
    }

    @AfterEach
    void verifyAndClean() {
        database.assertInvariants();
        database.clean();
    }

    @Test
    void al25DeAgostoDiezTokensA103YAlPrimeroDeSeptiembreQuinceA101SC005() {
        scenario();

        Portfolio august = portfolioService.at(ana.getId(), ana.getId(), "2026-08-25");
        Portfolio september = portfolioService.at(ana.getId(), ana.getId(), "2026-09-01");

        assertThat(august.date()).isEqualTo(AUGUST_25);
        assertThat(august.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(10);
            assertThat(position.quote().id()).isEqualTo(august24.id());
            assertThat(position.quote().tokenPrice()).isEqualByComparingTo("1.0300");
            assertThat(position.quote().score()).isEqualByComparingTo(SCORE_103);
            assertThat(position.value()).isEqualByComparingTo("10.30");
            assertThat(position.committed()).isNull();
        });
        assertThat(september.date()).isEqualTo(SEPTEMBER_1);
        assertThat(september.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(15);
            assertThat(position.quote().tokenPrice()).isEqualByComparingTo("1.0100");
            assertThat(position.value()).isEqualByComparingTo("15.15");
            assertThat(position.averagePrice()).isEqualByComparingTo("1.0100");
            assertThat(position.invested()).isEqualByComparingTo("15.15");
        });
    }

    @Test
    void elSaldoALaFechaSaleDeLasOperacionesYNoTraeLibreNiReservado() {
        scenario();

        Portfolio august = portfolioService.at(ana.getId(), ana.getId(), "2026-08-25");
        Portfolio september = portfolioService.at(ana.getId(), ana.getId(), "2026-09-01");
        Portfolio beforeTrading = portfolioService.at(ana.getId(), ana.getId(), "2026-08-19");

        assertThat(august.totals().balance()).isEqualByComparingTo("990.00");
        assertThat(august.totals().equity()).isEqualByComparingTo("1000.30");
        assertThat(august.totals().availableBalance()).isNull();
        assertThat(august.totals().reservedBalance()).isNull();
        assertThat(september.totals().balance()).isEqualByComparingTo("984.85");
        assertThat(database.account(ana.getId()).getBalance()).isEqualByComparingTo("979.80");
        assertThat(beforeTrading.positions()).isEmpty();
        assertThat(beforeTrading.totals().balance()).isEqualByComparingTo(INITIAL_BALANCE);
    }

    @Test
    void unaOperacionALas2330HoraDeArgentinaEntraEnEseDiaUS73() {
        clock.set(at("2026-08-20T23:30"));
        marketOrderService.buy(ana.getId(), kane.id(), 10);
        clock.set(at("2026-09-05T12:00"));

        Portfolio thatDay = portfolioService.at(ana.getId(), ana.getId(), "2026-08-20");
        Portfolio dayBefore = portfolioService.at(ana.getId(), ana.getId(), "2026-08-19");

        assertThat(thatDay.positions()).singleElement()
                .satisfies(position -> assertThat(position.tokens()).isEqualTo(10));
        assertThat(thatDay.totals().balance()).isEqualByComparingTo("990.00");
        assertThat(dayBefore.positions()).isEmpty();
    }

    @Test
    void laRealizadaIncluyeSoloLasVentasAnterioresUS74() {
        scenario();

        Portfolio august = portfolioService.at(bruno.getId(), bruno.getId(), "2026-08-25");
        Portfolio september = portfolioService.at(bruno.getId(), bruno.getId(), "2026-09-01");
        Portfolio current = portfolioService.current(bruno.getId(), bruno.getId());

        assertThat(august.totals().realizedProfit()).isEqualByComparingTo("0.00");
        assertThat(august.positions()).singleElement()
                .satisfies(position -> assertThat(position.tokens()).isEqualTo(90));
        assertThat(september.totals().realizedProfit()).isEqualByComparingTo("0.15");
        assertThat(september.positions()).singleElement()
                .satisfies(position -> assertThat(position.tokens()).isEqualTo(85));
        assertThat(current.totals().realizedProfit()).isEqualByComparingTo("0.20");
    }

    @Test
    void laFechaDeHoyEquivaleAlActualConLaCotizacionDelFinalDelDia() {
        scenario();

        Portfolio today = portfolioService.at(ana.getId(), ana.getId(), "2026-09-05");
        Portfolio current = portfolioService.current(ana.getId(), ana.getId());

        assertThat(today.positions()).extracting(PortfolioPosition::tokens)
                .containsExactlyElementsOf(current.positions().stream().map(PortfolioPosition::tokens).toList());
        assertThat(today.positions()).extracting(PortfolioPosition::value)
                .containsExactlyElementsOf(current.positions().stream().map(PortfolioPosition::value).toList());
        assertThat(today.positions().getFirst().invested()).isEqualByComparingTo(
                current.positions().getFirst().invested());
        assertThat(today.totals().balance()).isEqualByComparingTo(current.totals().balance());
        assertThat(today.totals().equity()).isEqualByComparingTo(current.totals().equity());
        assertThat(today.totals().realizedProfit()).isEqualByComparingTo(current.totals().realizedProfit());
    }

    @Test
    void elSuperusuarioAUnaFechaTieneLosTokensDeSusInicialesQueNoHabiaVendido() {
        scenario();
        long superuserId = database.superuser().getId();

        Portfolio beforeOpening = portfolioService.at(superuserId, superuserId, "2026-08-16");
        Portfolio afterFirstSale = portfolioService.at(superuserId, superuserId, "2026-08-20");
        Portfolio soldOut = portfolioService.at(superuserId, superuserId, "2026-08-25");

        assertThat(beforeOpening.positions()).isEmpty();
        assertThat(afterFirstSale.positions()).singleElement().satisfies(position -> {
            assertThat(position.tokens()).isEqualTo(90);
            assertThat(position.value()).isEqualByComparingTo("90.00");
            assertThat(position.averagePrice()).isNull();
            assertThat(position.committed()).isNull();
        });
        assertThat(afterFirstSale.totals().balance())
                .isEqualByComparingTo(beforeOpening.totals().balance().add(new BigDecimal("10.00")));
        assertThat(afterFirstSale.totals().realizedProfit()).isNull();
        assertThat(soldOut.positions()).isEmpty();
        assertThat(soldOut.totals().balance())
                .isEqualByComparingTo(beforeOpening.totals().balance().add(new BigDecimal("100.00")));
    }

    /**
     * Las operaciones de la clase, cada una con el reloj en su momento; al final el reloj queda en
     * el 05/09.
     */
    private void scenario() {
        clock.set(at("2026-08-20T12:00"));
        marketOrderService.buy(ana.getId(), kane.id(), 10);
        clock.set(at("2026-08-21T12:00"));
        marketOrderService.buy(bruno.getId(), kane.id(), 90);
        clock.set(at("2026-08-26T12:00"));
        marketOrderService.sell(bruno.getId(), kane.id(), 5);
        marketOrderService.buy(ana.getId(), kane.id(), 5);
        clock.set(at("2026-09-03T12:00"));
        marketOrderService.sell(bruno.getId(), kane.id(), 5);
        marketOrderService.buy(ana.getId(), kane.id(), 5);
        clock.set(at("2026-09-05T12:00"));
    }
}
