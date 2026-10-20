package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingReason;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationReport;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * El recálculo de punta a punta contra la H2, con el reloj quieto. El perfil {@code test} no tiene
 * token de Football-Data y apunta WhoScored a un host inválido: si el recálculo consultara una
 * fuente, fallaría.
 *
 * <p>Las semanas son la del 17/08, la del 24/08 y la del 31/08 de 2026, y el reloj está el lunes
 * 07/09 a la mañana, con la semana del 07/09 en curso. Juegan Everton y Manchester United un
 * partido por semana; la del 31/08 tiene además Arsenal - Liverpool, que se usa para dejarla sin
 * estadísticas.</p>
 *
 * <p>"Una semana pendiente bloquea las siguientes" no se puede ver acá: con la espera de 7 días, la
 * semana siguiente a una pendiente cierra justo cuando vence la espera (nota de T046). Lo prueba
 * {@code QuotingPlanTest} con una espera más larga.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:recalculation-it;DB_CLOSE_DELAY=-1")
class RecalculationIT {
    private static final QuoteWeek FIRST = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final QuoteWeek SECOND = FIRST.next();
    private static final QuoteWeek THIRD = SECOND.next();
    private static final QuoteWeek CURRENT = THIRD.next();
    private static final Instant NOW = CURRENT.start().plus(Duration.ofHours(9));

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private QuoteRecalculationService recalculationService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private Clock injectedClock;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private Team everton;
    private Team manUtd;
    private Team arsenal;
    private Team liverpool;
    private List<Player> active;
    private Player inactive;

    @BeforeEach
    void prepareDatabase() {
        clock.set(NOW);
        database = new QuoteDatabase(context);
        database.clean();
        loadSeason(true);
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void elServicioUsaElRelojDelTest() {
        assertThat(injectedClock).isSameAs(clock);
    }

    @Test
    void tresSemanasCerradasSinCotizacionesSeCotizanEnOrdenYCadaActivoQuedaConSuInicialYTresCalculadas() {
        RecalculationReport report = recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(report.quotedWeeks()).containsExactly(
                new WeekResult(FIRST, 4, 4, 0),
                new WeekResult(SECOND, 0, 4, 0),
                new WeekResult(THIRD, 0, 4, 0));
        assertThat(report.pendingWeeks()).isEmpty();
        assertThat(report.isEmpty()).isFalse();
        assertThat(active).allSatisfy(player -> assertThat(historyOf(player))
                .extracting(Quote::type, Quote::week, Quote::validFrom)
                .containsExactly(
                        tuple(QuoteType.INITIAL, FIRST, FIRST.start()),
                        tuple(QuoteType.CALCULATED, FIRST, FIRST.end()),
                        tuple(QuoteType.CALCULATED, SECOND, SECOND.end()),
                        tuple(QuoteType.CALCULATED, THIRD, THIRD.end())));
    }

    @Test
    void cadaSemanaParteDelValorQueDejoLaAnterior() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(active).allSatisfy(player -> {
            List<Quote> history = historyOf(player);
            for (int i = 1; i < history.size(); i++) {
                assertThat(history.get(i).baseValue()).isEqualTo(history.get(i - 1).value());
            }
        });
    }

    @Test
    void elGoleadorSubeYElQueNoConvirtioBajaRespectoDeSusPares() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        Quote ndiayeFirst = calculatedOf(active.get(0), FIRST);
        Quote betoFirst = calculatedOf(active.get(1), FIRST);
        assertThat(ndiayeFirst.value()).isGreaterThan(Quote.INITIAL_VALUE);
        assertThat(betoFirst.value()).isLessThan(Quote.INITIAL_VALUE);
    }

    @Test
    void repetirElRecalculoNoCreaNadaEInformaQueNoHabiaSemanasParaCotizar() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        long afterFirstRun = database.quoteCount();

        RecalculationReport second = recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(second.isEmpty()).isTrue();
        assertThat(second.quotedWeeks()).isEmpty();
        assertThat(database.quoteCount()).isEqualTo(afterFirstRun);
    }

    @Test
    void laSemanaEnCursoNoSeCotiza() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(database.quotesOf(CURRENT)).isEmpty();
        assertThat(database.quotes()).extracting(Quote::week).containsOnly(FIRST, SECOND, THIRD);
    }

    @Test
    void unJugadorInactivoNoRecibeCotizacion() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(database.quotesOf(inactive)).isEmpty();
    }

    @Test
    void unaSemanaConPartidosSinEstadisticasAMenosDeSieteDiasQuedaPendienteYLasAnterioresSeCotizan() {
        database.clean();
        loadSeason(false);

        RecalculationReport report = recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(report.quotedWeeks()).extracting(WeekResult::week).containsExactly(FIRST, SECOND);
        assertThat(report.pendingWeeks()).containsExactly(new PendingWeek(THIRD, PendingReason.MISSING_STATS));
        assertThat(database.quotesOf(THIRD)).isEmpty();
    }

    @Test
    void pasadosSieteDiasDelCierreLaSemanaSinEstadisticasSeCotizaConLoQueHay() {
        database.clean();
        loadSeason(false);
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        clock.set(THIRD.end().plus(Duration.ofDays(7)));

        RecalculationReport report = recalculationService.recalculate(RecalculationOrigin.WEEKLY);

        assertThat(report.quotedWeeks()).containsExactly(
                new WeekResult(THIRD, 0, 4, 0),
                new WeekResult(CURRENT, 0, 4, 0));
        assertThat(report.pendingWeeks()).isEmpty();
    }

    @Test
    void dosBasesIdenticasDanCotizacionesIdenticas() {
        recalculationService.recalculate(RecalculationOrigin.MANUAL);
        Map<String, List<String>> firstRun = quotesByPlayerExternalId();
        database.clean();
        loadSeason(true);

        recalculationService.recalculate(RecalculationOrigin.MANUAL);

        assertThat(quotesByPlayerExternalId()).isEqualTo(firstRun).isNotEmpty();
    }

    /**
     * Cuatro delanteros activos (tres de Everton y Manchester United y uno de Arsenal, que no juega
     * hasta la tercera semana) y un delantero inactivo. Con {@code thirdWeekStats} en falso, el
     * Arsenal - Liverpool de la tercera semana queda sin estadísticas.
     */
    private void loadSeason(boolean thirdWeekStats) {
        everton = database.team("62", "Everton FC");
        manUtd = database.team("66", "Manchester United FC");
        arsenal = database.team("57", "Arsenal FC");
        liverpool = database.team("64", "Liverpool FC");
        Player ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton);
        Player beto = database.player("8136", "Beto", Position.FORWARD, everton);
        Player zirkzee = database.player("8137", "Joshua Zirkzee", Position.FORWARD, manUtd);
        Player saka = database.player("8138", "Bukayo Saka", Position.FORWARD, arsenal);
        inactive = database.inactive(database.player("8139", "Dominic Calvert-Lewin", Position.FORWARD, everton));
        active = List.of(ndiaye, beto, zirkzee, saka);

        playWeek("560600", FIRST, 2, 1, ndiaye, 2, beto, 0, zirkzee, 1);
        playWeek("560601", SECOND, 0, 1, ndiaye, 0, beto, 0, zirkzee, 1);
        playWeek("560602", THIRD, 1, 1, ndiaye, 1, beto, 0, zirkzee, 1);
        Match arsenalLiverpool = database.finishedMatch("560603", THIRD, arsenal, liverpool, 3, 0, thirdWeekStats);
        if (thirdWeekStats) {
            database.stats(stats().forPlayer(saka).inMatch(arsenalLiverpool).minutes(90).goals(3).rating("8.9"));
        }
        database.scheduledMatch("560604", CURRENT, everton, manUtd);
    }

    private void playWeek(String externalId, QuoteWeek week, int evertonGoals, int manUtdGoals, Player ndiaye,
                          int ndiayeGoals, Player beto, int betoGoals, Player zirkzee, int zirkzeeGoals) {
        Match match = database.finishedMatch(externalId, week, everton, manUtd, evertonGoals, manUtdGoals, true);
        database.stats(
                stats().forPlayer(ndiaye).inMatch(match).minutes(90).goals(ndiayeGoals).shots(4).rating("7.5"),
                stats().forPlayer(beto).inMatch(match).minutes(70).goals(betoGoals).shots(1).rating("6.1"),
                stats().forPlayer(zirkzee).inMatch(match).minutes(90).goals(zirkzeeGoals).shots(2).rating("6.8"));
    }

    private List<Quote> historyOf(Player player) {
        return database.quotesOf(player).stream().sorted(Comparator.comparing(Quote::validFrom)).toList();
    }

    private Quote calculatedOf(Player player, QuoteWeek week) {
        return historyOf(player).stream()
                .filter(quote -> quote.type() == QuoteType.CALCULATED && quote.week().equals(week))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Las cotizaciones de cada jugador sin los ids, que cambian al recargar la base: tipo, semana,
     * vigencia, valor, base y score, en orden de vigencia.
     */
    private Map<String, List<String>> quotesByPlayerExternalId() {
        Map<Long, String> externalIds = active.stream()
                .collect(Collectors.toMap(Player::id, Player::externalId));
        return database.quotes().stream()
                .sorted(Comparator.comparing(Quote::validFrom))
                .collect(Collectors.groupingBy(quote -> externalIds.get(quote.playerId()),
                        Collectors.mapping(RecalculationIT::describe, Collectors.toList())));
    }

    private static String describe(Quote quote) {
        BigDecimal base = quote.baseValue();
        return "%s %s %s %s %s %s".formatted(quote.type(), quote.week().monday(), quote.validFrom(), quote.value(),
                base, quote.score());
    }
}
