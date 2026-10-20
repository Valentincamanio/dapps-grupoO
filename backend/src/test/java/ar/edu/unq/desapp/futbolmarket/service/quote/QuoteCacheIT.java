package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

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
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * El caché de la cotización actual y del ranking contra la H2 (research D21): se llena con la
 * primera consulta, no se entera de una escritura que no lo invalida, y un recálculo que cotiza lo
 * vacía al terminar.
 *
 * <p>Juegan Everton y Manchester United un partido por semana, desde la del 17/08/2026. El reloj
 * arranca el lunes 24/08 a la mañana, con la semana del 17/08 recién cerrada.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-cache-it;DB_CLOSE_DELAY=-1")
class QuoteCacheIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final Duration MONDAY_MORNING = Duration.ofHours(9);
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(SECOND.start().plus(MONDAY_MORNING));
        }
    }

    @Autowired
    private QuoteQueryService queryService;

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private QuoteCacheInvalidator cacheInvalidator;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private Team everton;
    private Team manUtd;
    private Player ndiaye;
    private Player zirkzee;

    @BeforeEach
    void prepareDatabase() {
        clock.set(SECOND.start().plus(MONDAY_MORNING));
        database = new QuoteDatabase(context);
        database.clean();
        cacheInvalidator.invalidateAll();
        everton = database.team("62", "Everton FC");
        manUtd = database.team("66", "Manchester United FC");
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
        cacheInvalidator.invalidateAll();
    }

    /**
     * La recotizada se guarda sin pasar por el invalidador: mientras el caché no se vacíe, la actual
     * y el ranking siguen siendo los de la primera consulta.
     */
    @Test
    void laSegundaConsultaDeLaActualYDelRankingSaleDelCache() {
        StrategyVersionRef strategy = strategyRepository.findCatalog().activeVersion().ref();
        quoteRepository.saveAll(List.of(
                new Quote(null, ndiaye.id(), FIRST, FIRST.start(), Quote.INITIAL_VALUE, null,
                        new BigDecimal("0.000000"), QuoteType.INITIAL, strategy),
                new Quote(null, ndiaye.id(), FIRST, FIRST.end(), new BigDecimal("103.00"), new BigDecimal("100.00"),
                        new BigDecimal("0.300000"), QuoteType.CALCULATED, strategy)));
        Quote firstCurrent = queryService.getCurrent(ndiaye.id());
        RankingPage firstRanking = queryService.getRanking(NO_FILTER, 0, 10);
        quoteRepository.saveAll(List.of(new Quote(null, ndiaye.id(), FIRST, FIRST.end().plus(Duration.ofHours(1)),
                new BigDecimal("104.00"), new BigDecimal("100.00"), new BigDecimal("0.400000"), QuoteType.REQUOTED,
                strategy)));

        Quote cachedCurrent = queryService.getCurrent(ndiaye.id());
        RankingPage cachedRanking = queryService.getRanking(NO_FILTER, 0, 10);
        cacheInvalidator.invalidateAll();
        Quote freshCurrent = queryService.getCurrent(ndiaye.id());
        RankingPage freshRanking = queryService.getRanking(NO_FILTER, 0, 10);

        assertThat(cachedCurrent).isSameAs(firstCurrent);
        assertThat(cachedRanking).isSameAs(firstRanking);
        assertThat(cachedCurrent.value()).isEqualTo(new BigDecimal("103.00"));
        assertThat(freshCurrent.type()).isEqualTo(QuoteType.REQUOTED);
        assertThat(freshCurrent.value()).isEqualTo(new BigDecimal("104.00"));
        assertThat(freshRanking.page().content().getFirst().quote().value()).isEqualTo(new BigDecimal("104.00"));
    }

    @Test
    void unRecalculoQueCotizaVaciaLosDosCachesYLaConsultaSiguienteVeLaSemanaNuevaCompleta() {
        playWeek("560600", FIRST);
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        assertThat(queryService.getCurrent(ndiaye.id()).week()).isEqualTo(FIRST);
        assertThat(queryService.getCurrent(zirkzee.id()).week()).isEqualTo(FIRST);
        assertThat(rankedWeeks(queryService.getRanking(NO_FILTER, 0, 10))).containsOnly(FIRST);
        playWeek("560601", SECOND);
        clock.set(SECOND.end().plus(MONDAY_MORNING));

        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        Quote ndiayeCurrent = queryService.getCurrent(ndiaye.id());
        Quote zirkzeeCurrent = queryService.getCurrent(zirkzee.id());
        RankingPage ranking = queryService.getRanking(NO_FILTER, 0, 10);
        assertThat(List.of(ndiayeCurrent, zirkzeeCurrent)).allSatisfy(quote -> {
            assertThat(quote.week()).isEqualTo(SECOND);
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
        });
        assertThat(ranking.page().content()).hasSize(2);
        assertThat(rankedWeeks(ranking)).containsOnly(SECOND);
    }

    /**
     * Everton 1 - 0 Manchester United el sábado de esa semana, con las estadísticas de los dos.
     */
    private void playWeek(String externalId, QuoteWeek week) {
        Match match = database.finishedMatch(externalId, week, everton, manUtd, 1, 0, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90));
    }

    private static List<QuoteWeek> rankedWeeks(RankingPage ranking) {
        return ranking.page().content().stream().map(RankedQuote::quote).map(Quote::week).toList();
    }
}
