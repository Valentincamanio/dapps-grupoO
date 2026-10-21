package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    void laUltimaAntesDeUnaSemanaEsLaDeMayorVigenciaDeLasSemanasAnterioresIncluidaUnaRecotizada() {
        Team manUtd = teamRepository.save(new Team(null, "66", "Manchester United FC", null, League.PREMIER, "32"));
        Player zirkzee = playerRepository.save(new Player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd));
        QuoteWeek next = WEEK.next();
        Quote requoted = new Quote(null, ndiaye.id(), WEEK, WEEK.end().plusSeconds(3600), new BigDecimal("104.00"),
                new BigDecimal("100.00"), new BigDecimal("0.400000"), QuoteType.REQUOTED, strategyA);
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                requoted, initial(zirkzee.id()), calculated(zirkzee.id(), WEEK, "99.00", "100.00"),
                calculated(zirkzee.id(), next, "98.00", "99.00")));

        Map<Long, Quote> lastBeforeNext = quoteRepository.findLastBeforeWeek(next);
        Map<Long, Quote> lastBeforeWeek = quoteRepository.findLastBeforeWeek(WEEK);

        assertThat(lastBeforeNext).containsOnlyKeys(ndiaye.id(), zirkzee.id());
        assertThat(lastBeforeNext.get(ndiaye.id()).type()).isEqualTo(QuoteType.REQUOTED);
        assertThat(lastBeforeNext.get(ndiaye.id()).value()).isEqualTo(new BigDecimal("104.00"));
        assertThat(lastBeforeNext.get(zirkzee.id()).value()).isEqualTo(new BigDecimal("99.00"));
        assertThat(lastBeforeNext.get(zirkzee.id()).strategy()).isEqualTo(strategyA);
        assertThat(lastBeforeWeek).isEmpty();
    }

    @Test
    void laUltimaSemanaCalculadaEsLaMayorConUnaCalculadaYSinNingunaNoHay() {
        assertThat(quoteRepository.findLastCalculatedWeek()).isEmpty();
        QuoteWeek next = WEEK.next();
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                calculated(ndiaye.id(), next, "101.00", "103.00")));

        assertThat(quoteRepository.findLastCalculatedWeek()).contains(next);
    }

    @Test
    void unaInicialSolaNoCuentaComoSemanaCalculada() {
        quoteRepository.saveAll(List.of(initial(ndiaye.id())));

        assertThat(quoteRepository.findLastCalculatedWeek()).isEmpty();
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

    @Test
    void laActualEsLaDeMayorVigenciaYSinCotizacionesNoHay() {
        QuoteWeek next = WEEK.next();
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                calculated(ndiaye.id(), next, "101.00", "103.00")));
        Player withoutQuotes = otherPlayer("8200", "Beto", Position.FORWARD);

        assertThat(quoteRepository.findCurrent(ndiaye.id())).get()
                .extracting(Quote::value, Quote::week, Quote::type, Quote::strategy)
                .containsExactly(new BigDecimal("101.00"), next, QuoteType.CALCULATED, strategyA);
        assertThat(quoteRepository.findCurrent(withoutQuotes.id())).isEmpty();
    }

    @Test
    void laActualDeUnaSemanaRecotizadaEsLaRecotizada() {
        Quote requoted = requoted(ndiaye.id(), WEEK, WEEK.end().plus(Duration.ofDays(2)), "104.00", "100.00");
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                requoted));

        assertThat(quoteRepository.findCurrent(ndiaye.id())).get()
                .extracting(Quote::type, Quote::value)
                .containsExactly(QuoteType.REQUOTED, new BigDecimal("104.00"));
    }

    @Test
    void lasActualesDeVariosJugadoresOmitenALosQueNoTienenNiAlQueNoExiste() {
        Player zirkzee = otherPlayer("8137", "Joshua Zirkzee", Position.FORWARD);
        Player withoutQuotes = otherPlayer("8200", "Beto", Position.FORWARD);
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                initial(zirkzee.id())));

        List<Quote> current = quoteRepository.findCurrentOf(List.of(ndiaye.id(), zirkzee.id(), withoutQuotes.id(),
                999_999L));

        assertThat(current)
                .extracting(Quote::playerId, Quote::value, Quote::type)
                .containsExactlyInAnyOrder(
                        tuple(ndiaye.id(), new BigDecimal("103.00"), QuoteType.CALCULATED),
                        tuple(zirkzee.id(), Quote.INITIAL_VALUE, QuoteType.INITIAL));
        assertThat(quoteRepository.findCurrentOf(List.of())).isEmpty();
    }

    @Test
    void laActualDeCadaJugadorEsLaDeMayorVigenciaConLasRecotizadasEInactivosYSinLosQueNoTienen() {
        QuoteWeek next = WEEK.next();
        Player zirkzee = otherPlayer("8137", "Joshua Zirkzee", Position.FORWARD);
        Player retired = playerRepository.save(otherPlayer("8139", "Dominic Calvert-Lewin", Position.FORWARD)
                .deactivate());
        Player withoutQuotes = otherPlayer("8200", "Beto", Position.FORWARD);
        quoteRepository.saveAll(List.of(
                initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                requoted(ndiaye.id(), WEEK, WEEK.end().plus(Duration.ofDays(2)), "104.00", "100.00"),
                requoted(ndiaye.id(), WEEK, WEEK.end().plus(Duration.ofDays(3)), "102.50", "100.00"),
                initial(zirkzee.id()), calculated(zirkzee.id(), WEEK, "103.00", "100.00"),
                calculated(zirkzee.id(), next, "101.00", "103.00"),
                initial(retired.id())));

        Map<Long, Quote> latest = quoteRepository.findLatestByPlayer();

        assertThat(latest).containsOnlyKeys(ndiaye.id(), zirkzee.id(), retired.id());
        assertThat(latest.get(ndiaye.id()))
                .extracting(Quote::type, Quote::value, Quote::strategy)
                .containsExactly(QuoteType.REQUOTED, new BigDecimal("102.50"), strategyA);
        assertThat(latest.get(zirkzee.id()))
                .extracting(Quote::type, Quote::week, Quote::value)
                .containsExactly(QuoteType.CALCULATED, next, new BigDecimal("101.00"));
        assertThat(latest.get(retired.id()).type()).isEqualTo(QuoteType.INITIAL);
        assertThat(latest).doesNotContainKey(withoutQuotes.id());
    }

    /**
     * El ejemplo del spec (US2.6): inicial de 100,00 desde el 17/08, 103,00 desde el 24/08 y 101,00
     * desde el 31/08.
     */
    @Test
    void laCotizacionAUnaFechaEsLaQueRegiaAlFinalDeEseDia() {
        QuoteWeek next = WEEK.next();
        quoteRepository.saveAll(List.of(initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                calculated(ndiaye.id(), next, "101.00", "103.00")));

        assertThat(quoteRepository.findAt(ndiaye.id(), QuoteDay.endOf(LocalDate.of(2026, 8, 20))))
                .get().extracting(Quote::value).isEqualTo(new BigDecimal("100.00"));
        assertThat(quoteRepository.findAt(ndiaye.id(), QuoteDay.endOf(LocalDate.of(2026, 8, 24))))
                .get().extracting(Quote::value).isEqualTo(new BigDecimal("103.00"));
        assertThat(quoteRepository.findAt(ndiaye.id(), QuoteDay.endOf(LocalDate.of(2026, 8, 31))))
                .get().extracting(Quote::value).isEqualTo(new BigDecimal("101.00"));
        assertThat(quoteRepository.findAt(ndiaye.id(), QuoteDay.endOf(LocalDate.of(2026, 8, 16)))).isEmpty();
    }

    @Test
    void elHistorialDeUnRangoEsAscendenteIncluyeLasReemplazadasYSePagina() {
        QuoteWeek next = WEEK.next();
        Quote requoted = requoted(ndiaye.id(), WEEK, WEEK.end().plus(Duration.ofDays(2)), "104.00", "100.00");
        quoteRepository.saveAll(List.of(calculated(ndiaye.id(), next, "101.00", "104.00"), requoted,
                initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00")));
        Instant from = QuoteDay.startOf(LocalDate.of(2026, 8, 24));
        Instant to = QuoteDay.endOf(LocalDate.of(2026, 8, 31));

        QuotePage<Quote> firstPage = quoteRepository.findHistory(ndiaye.id(), from, to, 0, 2);
        QuotePage<Quote> secondPage = quoteRepository.findHistory(ndiaye.id(), from, to, 1, 2);
        QuotePage<Quote> all = quoteRepository.findHistory(ndiaye.id(), Instant.EPOCH, to, 0, 20);

        assertThat(firstPage.content())
                .extracting(Quote::type, Quote::value)
                .containsExactly(
                        tuple(QuoteType.CALCULATED, new BigDecimal("103.00")),
                        tuple(QuoteType.REQUOTED, new BigDecimal("104.00")));
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.content()).extracting(Quote::value).containsExactly(new BigDecimal("101.00"));
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(all.content()).extracting(Quote::validFrom).isSorted().hasSize(4);
    }

    @Test
    void elHistorialDeUnRangoSinCotizacionesEstaVacio() {
        quoteRepository.saveAll(List.of(initial(ndiaye.id())));

        QuotePage<Quote> history = quoteRepository.findHistory(ndiaye.id(), QuoteDay.startOf(LocalDate.of(2026, 9, 1)),
                QuoteDay.endOf(LocalDate.of(2026, 9, 30)), 0, 20);

        assertThat(history.content()).isEmpty();
        assertThat(history.totalElements()).isZero();
    }

    @Test
    void elRankingOrdenaPorValorDescendenteYDesempataPorNombreYPorIdSinInactivosNiJugadoresSinCotizacion() {
        Ranking ranking = loadRanking();

        QuotePage<RankedQuote> page = quoteRepository.findRanking(new PlayerFilter(null, null, null), 0, 10);

        assertThat(page.content())
                .extracting(row -> row.player().id(), row -> row.quote().value())
                .containsExactly(
                        tuple(ranking.kane().id(), new BigDecimal("120.00")),
                        tuple(ranking.firstDanilo().id(), new BigDecimal("110.00")),
                        tuple(ranking.secondDanilo().id(), new BigDecimal("110.00")),
                        tuple(ndiaye.id(), new BigDecimal("103.00")),
                        tuple(ranking.zirkzee().id(), new BigDecimal("103.00")),
                        tuple(ranking.pickford().id(), new BigDecimal("99.00")));
        assertThat(page.content().getFirst().player().team().name()).isEqualTo("FC Bayern München");
        assertThat(page.content().getFirst().quote().strategy()).isEqualTo(strategyA);
        assertThat(page.totalElements()).isEqualTo(6);
    }

    @Test
    void elRankingCombinaLosFiltrosDeLigaEquipoYPosicion() {
        Ranking ranking = loadRanking();

        QuotePage<RankedQuote> premierForwards = quoteRepository.findRanking(
                new PlayerFilter(League.PREMIER, null, Position.FORWARD), 0, 10);
        QuotePage<RankedQuote> evertonForwards = quoteRepository.findRanking(
                new PlayerFilter(League.PREMIER, "Everton FC", Position.FORWARD), 0, 10);
        QuotePage<RankedQuote> bundesliga = quoteRepository.findRanking(
                new PlayerFilter(League.BUNDESLIGA, null, null), 0, 10);
        QuotePage<RankedQuote> bundesligaGoalkeepers = quoteRepository.findRanking(
                new PlayerFilter(League.BUNDESLIGA, null, Position.GOALKEEPER), 0, 10);

        assertThat(premierForwards.content()).extracting(row -> row.player().id())
                .containsExactly(ndiaye.id(), ranking.zirkzee().id());
        assertThat(evertonForwards.content()).extracting(row -> row.player().id()).containsExactly(ndiaye.id());
        assertThat(bundesliga.content()).extracting(row -> row.player().id()).containsExactly(ranking.kane().id());
        assertThat(bundesligaGoalkeepers.content()).isEmpty();
        assertThat(bundesligaGoalkeepers.totalElements()).isZero();
    }

    @Test
    void elRankingSePaginaConElMismoOrden() {
        Ranking ranking = loadRanking();

        QuotePage<RankedQuote> page = quoteRepository.findRanking(new PlayerFilter(null, null, null), 1, 2);

        assertThat(page.content()).extracting(row -> row.player().id())
                .containsExactly(ranking.secondDanilo().id(), ndiaye.id());
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
    }

    private record Ranking(Player kane, Player firstDanilo, Player secondDanilo, Player zirkzee, Player pickford) {
    }

    /**
     * Seis jugadores activos con cotización, uno inactivo con la más alta y uno sin ninguna. Los dos
     * Danilo empatan en valor y en nombre: los ordena el id. Ndiaye y Zirkzee empatan en valor: los
     * ordena el nombre.
     */
    private Ranking loadRanking() {
        Team manUtd = teamRepository.save(new Team(null, "66", "Manchester United FC", null, League.PREMIER, "32"));
        Team bayern = teamRepository.save(new Team(null, "5", "FC Bayern München", null, League.BUNDESLIGA, "37"));
        Team everton = ndiaye.team();
        Player kane = playerRepository.save(new Player("8300", "Harry Kane", Position.FORWARD, bayern));
        Player firstDanilo = playerRepository.save(new Player("8301", "Danilo", Position.MIDFIELDER, everton));
        Player secondDanilo = playerRepository.save(new Player("8302", "Danilo", Position.MIDFIELDER, manUtd));
        Player zirkzee = playerRepository.save(new Player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd));
        Player pickford = playerRepository.save(new Player("8303", "Jordan Pickford", Position.GOALKEEPER, everton));
        Player retired = playerRepository.save(new Player("8304", "Seamus Coleman", Position.FORWARD, everton));
        playerRepository.save(new Player("8305", "Beto", Position.FORWARD, everton));
        playerRepository.save(retired.deactivate());
        quoteRepository.saveAll(List.of(
                initial(kane.id()), calculated(kane.id(), WEEK, "120.00", "100.00"),
                initial(firstDanilo.id()), calculated(firstDanilo.id(), WEEK, "110.00", "100.00"),
                initial(secondDanilo.id()), calculated(secondDanilo.id(), WEEK, "110.00", "100.00"),
                initial(ndiaye.id()), calculated(ndiaye.id(), WEEK, "103.00", "100.00"),
                initial(zirkzee.id()), calculated(zirkzee.id(), WEEK, "103.00", "100.00"),
                initial(pickford.id()), calculated(pickford.id(), WEEK, "99.00", "100.00"),
                initial(retired.id()), calculated(retired.id(), WEEK, "200.00", "100.00")));
        return new Ranking(kane, firstDanilo, secondDanilo, zirkzee, pickford);
    }

    private Player otherPlayer(String externalId, String name, Position position) {
        return playerRepository.save(new Player(externalId, name, position, ndiaye.team()));
    }

    private Quote requoted(long playerId, QuoteWeek week, Instant validFrom, String value, String baseValue) {
        return new Quote(null, playerId, week, validFrom, new BigDecimal(value), new BigDecimal(baseValue),
                ZERO_SCORE, QuoteType.REQUOTED, strategyA);
    }

    private Quote initial(long playerId) {
        return new Quote(null, playerId, WEEK, WEEK.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategyA);
    }

    private Quote calculated(long playerId, QuoteWeek week, String value, String baseValue) {
        return new Quote(null, playerId, week, week.end(), new BigDecimal(value), new BigDecimal(baseValue),
                ZERO_SCORE, QuoteType.CALCULATED, strategyA);
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
