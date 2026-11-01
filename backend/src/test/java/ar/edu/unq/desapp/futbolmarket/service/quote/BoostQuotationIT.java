package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.BoostRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.BoostSQLDAO;

/**
 * El cierre de una semana con potenciados guardados, contra la base: la calculada de cada
 * potenciado lleva el factor de escala de la estrategia activa multiplicado por su porcentaje, y la
 * de los demás no cambia. Una recotización usa los potenciados de la semana en curso. Juegan Ndiaye (2 goles), Beto (0), Zirkzee (1) y Garnacho (1); Hojlund
 * juega sin convertir y no está potenciado.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:boost-quotation-it;DB_CLOSE_DELAY=-1")
class BoostQuotationIT {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));

    @Autowired
    private QuoteWeekWriter weekWriter;

    @Autowired
    private BoostRepository boostRepository;

    @Autowired
    private BoostSQLDAO boostDAO;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private QuoteDatabase database;
    private StrategyVersion strategyA;
    private Player ndiaye;
    private Player beto;
    private Player zirkzee;
    private Player garnacho;
    private Player hojlund;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanDatabase();
        strategyA = strategyRepository.findCatalog().activeVersion();
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        beto = database.player("8136", "Beto", Position.FORWARD, everton);
        zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        garnacho = database.player("8138", "Alejandro Garnacho", Position.FORWARD, manUtd);
        hojlund = database.player("8139", "Rasmus Hojlund", Position.FORWARD, manUtd);
        Match match = database.finishedMatch("560600", WEEK, everton, manUtd, 2, 2, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(2),
                stats().forPlayer(beto).inMatch(match).minutes(90),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(garnacho).inMatch(match).minutes(90).goals(1),
                stats().forPlayer(hojlund).inMatch(match).minutes(90));
    }

    @AfterEach
    void cleanDatabase() {
        boostDAO.deleteAll();
        database.clean();
    }

    @Test
    void cadaPotenciadoCotizaConSuFactorMultiplicadoYElRestoComoSiempre() {
        boostRepository.replace(new WeekBoosts(WEEK, List.of(new PlayerBoost(ndiaye.id(), 300),
                new PlayerBoost(beto.id(), 200), new PlayerBoost(zirkzee.id(), 250),
                new PlayerBoost(garnacho.id(), 400))));

        weekWriter.quoteWeek(WEEK, strategyA);

        Map<Long, Quote> calculated = calculatedOf(WEEK);
        assertThat(calculated.get(ndiaye.id()).value()).isEqualTo(expected(calculated.get(ndiaye.id()), "3"));
        assertThat(calculated.get(beto.id()).value()).isEqualTo(expected(calculated.get(beto.id()), "2"));
        assertThat(calculated.get(zirkzee.id()).value()).isEqualTo(expected(calculated.get(zirkzee.id()), "2.5"));
        assertThat(calculated.get(garnacho.id()).value()).isEqualTo(expected(calculated.get(garnacho.id()), "4"));
        assertThat(calculated.get(hojlund.id()).value()).isEqualTo(expected(calculated.get(hojlund.id()), "1"));
    }

    @Test
    void sinPotenciadosGuardadosCotizaComoSiempre() {
        weekWriter.quoteWeek(WEEK, strategyA);

        Map<Long, Quote> calculated = calculatedOf(WEEK);
        assertThat(calculated.values()).allSatisfy(quote -> assertThat(quote.value()).isEqualTo(expected(quote, "1")));
    }

    /**
     * Cotizada la semana sin potenciados, se recotiza con la otra estrategia el miércoles de la
     * semana siguiente, que tiene sus propios potenciados: esos son los que rigen. Hojlund no está
     * potenciado.
     */
    @Test
    void recotizarDuranteLaSemanaSiguienteUsaLosPotenciadosDeEsaSemana() {
        weekWriter.quoteWeek(WEEK, strategyA);
        boostRepository.replace(new WeekBoosts(WEEK.next(), List.of(new PlayerBoost(ndiaye.id(), 400),
                new PlayerBoost(beto.id(), 300), new PlayerBoost(zirkzee.id(), 200),
                new PlayerBoost(garnacho.id(), 250))));
        StrategyVersion strategyB = strategyRepository.findCatalog()
                .findByName(StartingStrategies.BALANCED_BY_POSITION_NAME).latest();
        Instant now = WEEK.next().start().plus(Duration.ofDays(2));

        transactionTemplate.executeWithoutResult(status -> weekWriter.requoteLastWeek(strategyB, now,
                RecalculationReport.builder(RecalculationOrigin.STRATEGY_CHANGE, now, strategyB.ref())));

        Map<Long, Quote> requoted = quotesOf(WEEK, QuoteType.REQUOTED);
        assertThat(requoted).hasSize(5);
        assertThat(requoted.get(ndiaye.id()).value()).isEqualTo(expected(requoted.get(ndiaye.id()), strategyB, "4"));
        assertThat(requoted.get(beto.id()).value()).isEqualTo(expected(requoted.get(beto.id()), strategyB, "3"));
        assertThat(requoted.get(garnacho.id()).value())
                .isEqualTo(expected(requoted.get(garnacho.id()), strategyB, "2.5"));
        assertThat(requoted.get(hojlund.id()).value()).isEqualTo(expected(requoted.get(hojlund.id()), strategyB, "1"));
    }

    private BigDecimal expected(Quote quote, String multiplier) {
        return expected(quote, strategyA, multiplier);
    }

    /**
     * {@code max(mínimo, base + score × factor × multiplicador)}: es la primera semana, la base es 100.
     */
    private static BigDecimal expected(Quote quote, StrategyVersion strategy, String multiplier) {
        return quote.baseValue()
                .add(quote.score().multiply(strategy.scaleFactor()).multiply(new BigDecimal(multiplier)))
                .max(strategy.minimumValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, Quote> calculatedOf(QuoteWeek week) {
        return quotesOf(week, QuoteType.CALCULATED);
    }

    private Map<Long, Quote> quotesOf(QuoteWeek week, QuoteType type) {
        return database.quotesOf(week).stream()
                .filter(quote -> quote.type() == type)
                .collect(Collectors.toMap(Quote::playerId, Function.identity()));
    }
}
