package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.QuoteMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.QuoteSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las cotizaciones contra la base, con una H2 propia. La estrategia es la A que siembra
 * {@code StrategySeeder} al arrancar.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-it;DB_CLOSE_DELAY=-1")
class QuoteRepositoryIT {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.000000");

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private QuoteSQLDAO quoteDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    @Autowired
    private QuoteMapper quoteMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private StrategyVersionRef strategyA;
    private Player ndiaye;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        strategyA = strategyRepository.findCatalog().activeVersion().ref();
        Team everton = teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));
        ndiaye = playerRepository.save(new Player("8135", "Iliman Ndiaye", Position.FORWARD, everton));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void idaYVueltaConservaEscalasTipoSemanaVigenciaYVersion() {
        Quote initial = initial(ndiaye.id());
        Quote calculated = new Quote(null, ndiaye.id(), WEEK, WEEK.end(), new BigDecimal("103.00"),
                new BigDecimal("100.00"), new BigDecimal("0.300000"), QuoteType.CALCULATED, strategyA);

        List<Quote> saved = quoteRepository.saveAll(List.of(initial, calculated));

        List<Quote> read = readAll();
        assertThat(saved).allSatisfy(quote -> assertThat(quote.id()).isNotNull());
        assertThat(read).hasSize(2);
        Quote readCalculated = read.stream().filter(quote -> quote.type() == QuoteType.CALCULATED).findFirst().orElseThrow();
        assertThat(readCalculated.value()).isEqualTo(new BigDecimal("103.00"));
        assertThat(readCalculated.baseValue()).isEqualTo(new BigDecimal("100.00"));
        assertThat(readCalculated.score()).isEqualTo(new BigDecimal("0.300000"));
        assertThat(readCalculated.tokenPrice()).isEqualTo(new BigDecimal("1.0300"));
        assertThat(readCalculated.week()).isEqualTo(WEEK);
        assertThat(readCalculated.validFrom()).isEqualTo(WEEK.end());
        assertThat(readCalculated.strategy()).isEqualTo(strategyA);
        assertThat(readCalculated.playerId()).isEqualTo(ndiaye.id());
        Quote readInitial = read.stream().filter(quote -> quote.type() == QuoteType.INITIAL).findFirst().orElseThrow();
        assertThat(readInitial.baseValue()).isNull();
        assertThat(readInitial.validFrom()).isEqualTo(WEEK.start());
    }

    @Test
    void elIndiceUnicoRechazaDosCotizacionesDelMismoJugadorEnElMismoInstante() {
        quoteRepository.saveAll(List.of(initial(ndiaye.id())));

        assertThatThrownBy(() -> quoteRepository.saveAll(List.of(initial(ndiaye.id()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(quoteDAO.count()).isOne();
    }

    @Test
    void elRepositoryNoExponeNingunMetodoParaModificarNiBorrar() {
        List<String> publicMethods = Arrays.stream(QuoteRepository.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(publicMethods)
                .isNotEmpty()
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete") || name.startsWith("remove"));
    }

    private Quote initial(long playerId) {
        return new Quote(null, playerId, WEEK, WEEK.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategyA);
    }

    /**
     * El mapper lee el nombre de la versión, que es una asociación lazy: se lee dentro de una
     * transacción, como lo hacen los métodos de lectura del repository.
     */
    private List<Quote> readAll() {
        return transactionTemplate.execute(status -> quoteDAO.findAll().stream().map(quoteMapper::toDomain).toList());
    }

    private void cleanDatabase() {
        quoteDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
