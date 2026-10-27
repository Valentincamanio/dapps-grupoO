package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * Una semana de cotizaciones contra la base: se guarda completa o no queda nada (FR-012, SC-016).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-week-writer-it;DB_CLOSE_DELAY=-1")
class QuoteWeekWriterIT {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));

    @Autowired
    private QuoteWeekWriter weekWriter;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private StrategyVersion strategyA;
    private Player ndiaye;
    private Player beto;
    private Player zirkzee;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        database.clean();
        strategyA = strategyRepository.findCatalog().activeVersion();
        Team everton = database.team("62", "Everton FC");
        Team manUtd = database.team("66", "Manchester United FC");
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        beto = database.player("8136", "Beto", Position.FORWARD, everton);
        zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Match match = database.finishedMatch("560600", WEEK, everton, manUtd, 2, 1, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(2),
                stats().forPlayer(beto).inMatch(match).minutes(90),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).goals(1));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void guardaLaSemanaCompletaYDevuelveLasCantidadesPorTipo() {
        WeekResult result = weekWriter.quoteWeek(WEEK, strategyA);

        assertThat(result).isEqualTo(new WeekResult(WEEK, 3, 3, 0));
        assertThat(database.quotesOf(WEEK))
                .hasSize(6)
                .allSatisfy(quote -> assertThat(quote.strategy()).isEqualTo(strategyA.ref()));
    }

    /**
     * La inicial de la semana siguiente de Beto rige justo desde el cierre de esta semana, el mismo
     * instante que su calculada: el índice único la rechaza cuando las de Ndiaye ya se insertaron.
     */
    @Test
    void unaFallaALaMitadDeLaSemanaNoDejaNingunaCotizacionDeEsaSemana() {
        Quote conflicting = quoteRepository.saveAll(
                List.of(QuoteFixtures.initial(beto.id(), WEEK.next(), strategyA))).getFirst();

        assertThatThrownBy(() -> weekWriter.quoteWeek(WEEK, strategyA)).isInstanceOf(DataAccessException.class);

        assertThat(database.quotesOf(WEEK)).isEmpty();
        assertThat(database.quotes()).containsExactly(conflicting);
    }

    @Test
    void despuesDeLaFallaLaSiguienteCorridaCotizaLaSemana() {
        Quote conflicting = quoteRepository.saveAll(
                List.of(QuoteFixtures.initial(beto.id(), WEEK.next(), strategyA))).getFirst();
        assertThatThrownBy(() -> weekWriter.quoteWeek(WEEK, strategyA)).isInstanceOf(DataAccessException.class);
        database.deleteQuote(conflicting);

        WeekResult result = weekWriter.quoteWeek(WEEK, strategyA);

        assertThat(result).isEqualTo(new WeekResult(WEEK, 3, 3, 0));
        assertThat(database.quotesOf(WEEK)).hasSize(6);
    }

    @Test
    void laCalculadaParteDeLaUltimaCotizacionDeLasSemanasAnteriores() {
        weekWriter.quoteWeek(WEEK.previous(), strategyA);

        WeekResult result = weekWriter.quoteWeek(WEEK, strategyA);

        assertThat(result).isEqualTo(new WeekResult(WEEK, 0, 3, 0));
        assertThat(database.quotesOf(WEEK))
                .allSatisfy(quote -> assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED))
                .extracting(Quote::baseValue)
                .containsOnly(Quote.INITIAL_VALUE);
    }
}
