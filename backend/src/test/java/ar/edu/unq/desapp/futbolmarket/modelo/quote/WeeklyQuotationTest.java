package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.ValuationMetric;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.WeightSet;
import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder.stats;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ARSENAL_LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.BAYERN;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.BETO;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.DORTMUND;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON_MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.FERNANDES;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.GREALISH;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.KANE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.MAGUIRE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.SAKA;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_A;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.TARKOWSKI;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ZIRKZEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyQuotationTest {
    private static final List<Player> FORWARDS = List.of(NDIAYE, BETO, ZIRKZEE, SAKA, KANE);
    private static final QuoteWeek NEXT_WEEK = WEEK.next();
    private static final Match BAYERN_DORTMUND = QuoteFixtures.finishedMatch("560700", WEEK, BAYERN, DORTMUND, 3, 0);
    private static final Instant REQUOTED_AT = NEXT_WEEK.end().plus(Duration.ofDays(2)).plus(Duration.ofHours(10));
    private static final StrategyVersion REQUOTE_B = requoteStrategy(12L, "B",
            new WeightSet(Map.of(ValuationMetric.GOALS, new BigDecimal("0.50"),
                    ValuationMetric.TACKLES, new BigDecimal("0.50"))));
    private static final StrategyVersion REQUOTE_C = requoteStrategy(13L, "C",
            new WeightSet(Map.of(ValuationMetric.TACKLES, BigDecimal.ONE)));

    @Test
    void laPrimeraSemanaGeneraLaInicialDesdeElLunesYLaCalculadaDesdeElCierre() {
        var quotes = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, List.of(NDIAYE), List.of(), Map.of());

        assertThat(quotes).hasSize(2);
        var initial = quotes.get(0);
        assertThat(initial.type()).isEqualTo(QuoteType.INITIAL);
        assertThat(initial.validFrom()).isEqualTo(WEEK.start());
        assertThat(initial.value()).isEqualTo(new BigDecimal("100.00"));
        assertThat(initial.baseValue()).isNull();
        var calculated = quotes.get(1);
        assertThat(calculated.type()).isEqualTo(QuoteType.CALCULATED);
        assertThat(calculated.validFrom()).isEqualTo(WEEK.end());
        assertThat(calculated.baseValue()).isEqualTo(new BigDecimal("100.00"));
        assertThat(quotes).allSatisfy(quote -> {
            assertThat(quote.playerId()).isEqualTo(NDIAYE.id());
            assertThat(quote.week()).isEqualTo(WEEK);
            assertThat(quote.strategy()).isEqualTo(STRATEGY_A.ref());
        });
    }

    @Test
    void cienPasaACientoTresYDespuesACientoUnoEnDosSemanas() {
        var firstWeek = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, FORWARDS, specExampleWeek(), Map.of());
        var ndiayeFirst = calculatedOf(firstWeek, NDIAYE);

        var secondWeek = WeeklyQuotation.quoteWeek(NEXT_WEEK, STRATEGY_A, FORWARDS, ndiayeScoresNoGoals(),
                lastByPlayer(firstWeek));
        var ndiayeSecond = calculatedOf(secondWeek, NDIAYE);

        assertThat(ndiayeFirst.score()).isEqualTo(new BigDecimal("0.300000"));
        assertThat(ndiayeFirst.value()).isEqualTo(new BigDecimal("103.00"));
        assertThat(ndiayeFirst.tokenPrice()).isEqualTo(new BigDecimal("1.0300"));
        assertThat(ndiayeSecond.score()).isEqualTo(new BigDecimal("-0.200000"));
        assertThat(ndiayeSecond.baseValue()).isEqualTo(new BigDecimal("103.00"));
        assertThat(ndiayeSecond.value()).isEqualTo(new BigDecimal("101.00"));
        assertThat(secondWeek).noneMatch(quote -> quote.type() == QuoteType.INITIAL);
    }

    @Test
    void unDelanteroQueNoJugoSeCotizaContraLosQueConvirtieron() {
        var secondWeek = WeeklyQuotation.quoteWeek(NEXT_WEEK, STRATEGY_A, FORWARDS, ndiayeScoresNoGoals(),
                Map.of());

        assertThat(secondWeek.stream().filter(quote -> quote.type() == QuoteType.CALCULATED)).hasSize(5);
        assertThat(calculatedOf(secondWeek, NDIAYE).value()).isEqualTo(new BigDecimal("98.00"));
        assertThat(calculatedOf(secondWeek, KANE).value()).isEqualTo(new BigDecimal("102.00"));
    }

    @Test
    void conScoreCeroSeGuardaUnaCotizacionConElMismoValor() {
        var previous = QuoteFixtures.calculated(PICKFORD.id(), WEEK.previous(), "97.40", "100.00", "-0.260000",
                STRATEGY_A);
        var weekStats = List.of(stats().forPlayer(PICKFORD).minutes(90).rating("7.10").build());

        var quotes = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, List.of(PICKFORD), weekStats,
                Map.of(PICKFORD.id(), previous));

        assertThat(quotes).singleElement().satisfies(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
            assertThat(quote.score()).isEqualTo(new BigDecimal("0.000000"));
            assertThat(quote.baseValue()).isEqualTo(new BigDecimal("97.40"));
            assertThat(quote.value()).isEqualTo(new BigDecimal("97.40"));
        });
    }

    @Test
    void unaSemanaSinPartidosNoCambiaNingunValor() {
        var players = List.of(NDIAYE, BETO, GREALISH, TARKOWSKI, PICKFORD);
        var previous = Map.of(
                NDIAYE.id(), previousQuote(NDIAYE, "103.00"),
                BETO.id(), previousQuote(BETO, "96.50"),
                GREALISH.id(), previousQuote(GREALISH, "100.00"),
                TARKOWSKI.id(), previousQuote(TARKOWSKI, "88.20"),
                PICKFORD.id(), previousQuote(PICKFORD, "110.00"));

        var quotes = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, players, List.of(), previous);

        assertThat(quotes).hasSize(5).allSatisfy(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
            assertThat(quote.score()).isEqualTo(new BigDecimal("0.000000"));
            assertThat(quote.value()).isEqualTo(previous.get(quote.playerId()).value());
        });
    }

    @Test
    void unJugadorInactivoNoRecibeCotizacionYAlVolverParteDeSuUltima() {
        var inactiveBeto = QuoteFixtures.inactive(BETO);
        var betoLast = QuoteFixtures.calculated(BETO.id(), WEEK.previous().previous(), "95.00", "100.00",
                "-0.500000", STRATEGY_A);

        var whileInactive = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, List.of(NDIAYE, inactiveBeto), List.of(),
                Map.of(BETO.id(), betoLast));
        var afterComingBack = WeeklyQuotation.quoteWeek(NEXT_WEEK, STRATEGY_A, List.of(NDIAYE, BETO), List.of(),
                Map.of(BETO.id(), betoLast));

        assertThat(whileInactive).noneMatch(quote -> quote.playerId() == BETO.id());
        var beto = afterComingBack.stream().filter(quote -> quote.playerId() == BETO.id()).toList();
        assertThat(beto).singleElement().satisfies(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
            assertThat(quote.baseValue()).isEqualTo(new BigDecimal("95.00"));
            assertThat(quote.value()).isEqualTo(new BigDecimal("95.00"));
        });
    }

    @Test
    void unJugadorNuevoAMitadDeTemporadaRecibeSuInicialDesdeElLunesDeSuPrimeraSemana() {
        var previous = Map.of(NDIAYE.id(), previousQuote(NDIAYE, "103.00"));

        var quotes = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, List.of(NDIAYE, BETO), List.of(), previous);

        var beto = quotes.stream().filter(quote -> quote.playerId() == BETO.id()).toList();
        assertThat(beto).extracting(Quote::type).containsExactly(QuoteType.INITIAL, QuoteType.CALCULATED);
        assertThat(beto.get(0).validFrom()).isEqualTo(WEEK.start());
        assertThat(beto.get(1).baseValue()).isEqualTo(new BigDecimal("100.00"));
        assertThat(quotes.stream().filter(quote -> quote.playerId() == NDIAYE.id()))
                .extracting(Quote::type).containsExactly(QuoteType.CALCULATED);
    }

    @Test
    void unaMetricaQueNoSePuedeCalcularQuedaFueraDeLaComparacion() {
        var ratingOnly = strategy(new WeightSet(Map.of(ValuationMetric.RATING, BigDecimal.ONE)), Map.of());
        var weekStats = List.of(
                stats().forPlayer(NDIAYE).minutes(90).rating("7.00").build(),
                stats().forPlayer(BETO).minutes(90).rating("8.00").build());

        var quotes = WeeklyQuotation.quoteWeek(WEEK, ratingOnly, List.of(NDIAYE, BETO, ZIRKZEE), weekStats,
                Map.of());

        assertThat(calculatedOf(quotes, NDIAYE).value()).isEqualTo(new BigDecimal("95.00"));
        assertThat(calculatedOf(quotes, BETO).value()).isEqualTo(new BigDecimal("105.00"));
        assertThat(calculatedOf(quotes, ZIRKZEE).score()).isEqualTo(new BigDecimal("0.000000"));
        assertThat(calculatedOf(quotes, ZIRKZEE).value()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void cadaPosicionUsaSusPesosYSinoLosPorDefecto() {
        var forwardsByGoals = strategy(new WeightSet(Map.of(ValuationMetric.TACKLES, BigDecimal.ONE)),
                Map.of(Position.FORWARD, new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE))));
        var weekStats = List.of(
                stats().forPlayer(NDIAYE).minutes(90).goals(1).build(),
                stats().forPlayer(BETO).minutes(90).tackles(3).build(),
                stats().forPlayer(TARKOWSKI).minutes(90).goals(2).build(),
                stats().forPlayer(MAGUIRE).inMatch(EVERTON_MAN_UTD).minutes(90).tackles(1).build());

        var quotes = WeeklyQuotation.quoteWeek(WEEK, forwardsByGoals, List.of(NDIAYE, BETO, TARKOWSKI, MAGUIRE),
                weekStats, Map.of());

        assertThat(calculatedOf(quotes, NDIAYE).value()).isEqualTo(new BigDecimal("105.00"));
        assertThat(calculatedOf(quotes, BETO).value()).isEqualTo(new BigDecimal("95.00"));
        assertThat(calculatedOf(quotes, MAGUIRE).value()).isEqualTo(new BigDecimal("105.00"));
        assertThat(calculatedOf(quotes, TARKOWSKI).value()).isEqualTo(new BigDecimal("95.00"));
    }

    @Test
    void losParesSonDeLaMismaPosicion() {
        var goalsOnly = strategy(new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE)), Map.of());
        var weekStats = List.of(
                stats().forPlayer(NDIAYE).minutes(90).goals(1).build(),
                stats().forPlayer(GREALISH).minutes(90).goals(3).build());

        var quotes = WeeklyQuotation.quoteWeek(WEEK, goalsOnly, List.of(NDIAYE, GREALISH), weekStats, Map.of());

        assertThat(calculatedOf(quotes, NDIAYE).score()).isEqualTo(new BigDecimal("0.000000"));
        assertThat(calculatedOf(quotes, GREALISH).score()).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void lasEstadisticasDeOtraSemanaNoCuentan() {
        var goalsOnly = strategy(new WeightSet(Map.of(ValuationMetric.GOALS, BigDecimal.ONE)), Map.of());
        var nextWeekMatch = QuoteFixtures.finishedMatch("560701", NEXT_WEEK, EVERTON, MAN_UTD, 4, 0);
        var weekStats = List.of(stats().forPlayer(NDIAYE).inMatch(nextWeekMatch).minutes(90).goals(4).build());

        var quotes = WeeklyQuotation.quoteWeek(WEEK, goalsOnly, List.of(NDIAYE, BETO), weekStats, Map.of());

        assertThat(calculatedOf(quotes, NDIAYE).score()).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void elResultadoNoDependeDelOrdenDeLaEntrada() {
        var players = new ArrayList<>(List.of(NDIAYE, BETO, ZIRKZEE, SAKA, KANE, GREALISH, FERNANDES, TARKOWSKI,
                MAGUIRE, PICKFORD));
        var weekStats = new ArrayList<>(specExampleWeek());
        weekStats.add(stats().forPlayer(GREALISH).minutes(90).assists(1).rating("7.30").build());
        weekStats.add(stats().forPlayer(FERNANDES).inMatch(EVERTON_MAN_UTD).minutes(70).goals(1).build());
        weekStats.add(stats().forPlayer(TARKOWSKI).minutes(90).tackles(4).rating("6.90").build());
        var expected = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, players, weekStats, Map.of());

        Collections.shuffle(players, new Random(7));
        Collections.reverse(weekStats);
        Collections.shuffle(weekStats, new Random(11));
        var shuffled = WeeklyQuotation.quoteWeek(WEEK, STRATEGY_A, players, weekStats, Map.of());

        assertThat(shuffled).isEqualTo(expected);
        assertThat(shuffled).extracting(Quote::playerId).isSorted();
    }

    @Test
    void losScoresDeLaSemanaVienenOrdenadosPorJugador() {
        var scores = WeeklyQuotation.weekScores(WEEK, STRATEGY_A, List.of(KANE, NDIAYE, SAKA, BETO, ZIRKZEE),
                specExampleWeek());

        assertThat(scores.keySet()).containsExactly(NDIAYE.id(), BETO.id(), ZIRKZEE.id(), SAKA.id(), KANE.id());
        assertThat(scores.get(NDIAYE.id())).isEqualTo(new BigDecimal("0.300000"));
    }

    @Test
    void recotizarConBParteDelPrecioPrevioALaSemanaYConservaLaCalculada() {
        var ndiayeHistory = specHistoryWithA();

        var requoted = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)), REQUOTED_AT);

        assertThat(requoted).singleElement().satisfies(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.REQUOTED);
            assertThat(quote.playerId()).isEqualTo(NDIAYE.id());
            assertThat(quote.week()).isEqualTo(NEXT_WEEK);
            assertThat(quote.validFrom()).isEqualTo(REQUOTED_AT);
            assertThat(quote.score()).isEqualTo(new BigDecimal("0.100000"));
            assertThat(quote.baseValue()).isEqualTo(new BigDecimal("103.00"));
            assertThat(quote.value()).isEqualTo(new BigDecimal("104.00"));
            assertThat(quote.strategy()).isEqualTo(REQUOTE_B.ref());
        });
        assertThat(ndiayeHistory.get(1).value()).isEqualTo(new BigDecimal("101.00"));
    }

    @Test
    void laRecotizadaLlevaElMismoValorBaseQueLaCalculadaQueReemplaza() {
        var ndiayeHistory = specHistoryWithA();
        var replaced = ndiayeHistory.get(1);

        var requoted = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), replaced), REQUOTED_AT);

        assertThat(requoted.getFirst().baseValue()).isEqualTo(replaced.baseValue());
    }

    @Test
    void repetirLaRecotizacionConLaMismaVersionNoGeneraNada() {
        var ndiayeHistory = specHistoryWithA();
        var first = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)), REQUOTED_AT);

        var again = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), first.getFirst()),
                REQUOTED_AT.plus(Duration.ofHours(1)));

        assertThat(again).isEmpty();
    }

    @Test
    void recotizarConOtraEstrategiaVuelveAPartirDelPrecioPrevioYNoAcumula() {
        var ndiayeHistory = specHistoryWithA();
        var withB = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)), REQUOTED_AT)
                .getFirst();

        var withC = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_C, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), withB),
                REQUOTED_AT.plus(Duration.ofHours(1)));

        assertThat(withB.value()).isEqualTo(new BigDecimal("104.00"));
        assertThat(withC).singleElement().satisfies(quote -> {
            assertThat(quote.baseValue()).isEqualTo(new BigDecimal("103.00"));
            assertThat(quote.score()).isEqualTo(new BigDecimal("-0.200000"));
            assertThat(quote.value()).isEqualTo(new BigDecimal("101.00"));
            assertThat(quote.strategy()).isEqualTo(REQUOTE_C.ref());
        });
    }

    @Test
    void soloSeRecotizaAQuienTieneUnaCotizacionEnEsaSemanaPeroTodosLosActivosSonPares() {
        var ndiayeHistory = specHistoryWithA();

        var requoted = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)), REQUOTED_AT);
        var ndiayeAlone = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, List.of(NDIAYE), requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)), REQUOTED_AT);

        assertThat(requoted).extracting(Quote::playerId).containsExactly(NDIAYE.id());
        assertThat(ndiayeAlone.getFirst().score()).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void sinCotizacionAnteriorALaSemanaLaRecotizadaParteDeCien() {
        var calculated = QuoteFixtures.calculated(BETO.id(), NEXT_WEEK, "98.00", "100.00", "-0.200000", STRATEGY_A);

        var requoted = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(), Map.of(),
                Map.of(BETO.id(), calculated), REQUOTED_AT);

        assertThat(requoted).singleElement().satisfies(quote -> {
            assertThat(quote.playerId()).isEqualTo(BETO.id());
            assertThat(quote.baseValue()).isEqualTo(new BigDecimal("100.00"));
        });
    }

    @Test
    void unJugadorInactivoNoSeRecotiza() {
        var inactiveBeto = QuoteFixtures.inactive(BETO);
        var ndiayeHistory = specHistoryWithA();
        var betoCalculated = QuoteFixtures.calculated(BETO.id(), NEXT_WEEK, "98.00", "100.00", "-0.200000",
                STRATEGY_A);

        var requoted = WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B,
                List.of(NDIAYE, inactiveBeto, ZIRKZEE, SAKA, KANE), requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)),
                Map.of(NDIAYE.id(), ndiayeHistory.get(1), BETO.id(), betoCalculated), REQUOTED_AT);

        assertThat(requoted).extracting(Quote::playerId).containsExactly(NDIAYE.id());
    }

    @Test
    void noSeRecotizaUnaSemanaQueNoCerro() {
        var ndiayeHistory = specHistoryWithA();

        assertThatThrownBy(() -> WeeklyQuotation.requoteWeek(NEXT_WEEK, REQUOTE_B, FORWARDS, requoteWeekStats(),
                Map.of(NDIAYE.id(), ndiayeHistory.get(0)), Map.of(NDIAYE.id(), ndiayeHistory.get(1)),
                NEXT_WEEK.end().minusSeconds(1)))
                .isInstanceOf(QuoteInvariantException.class);
    }

    /**
     * El historial del ejemplo del spec (US5.3) con la estrategia A: calculada de 103,00 en
     * {@code WEEK} y de 101,00 en {@code NEXT_WEEK}, con score −0,20.
     */
    private static List<Quote> specHistoryWithA() {
        return List.of(
                QuoteFixtures.calculated(NDIAYE.id(), WEEK, "103.00", "100.00", "0.300000", STRATEGY_A),
                QuoteFixtures.calculated(NDIAYE.id(), NEXT_WEEK, "101.00", "103.00", "-0.200000", STRATEGY_A));
    }

    /**
     * La semana que se recotiza: con la estrategia B de la recotización (mitad goles, mitad quites),
     * Ndiaye queda con goles +0,4 (3 goles, solo Kane hizo más) y quites −0,2 (empata con Beto en 1 y
     * solo Zirkzee tiene menos), un score de +0,10. Con C (solo quites) queda en −0,20.
     */
    private static List<PlayerMatchStats> requoteWeekStats() {
        var everton = QuoteFixtures.finishedMatch("560900", NEXT_WEEK, EVERTON, MAN_UTD, 4, 1);
        var arsenal = QuoteFixtures.finishedMatch("560901", NEXT_WEEK, QuoteFixtures.ARSENAL,
                QuoteFixtures.LIVERPOOL, 2, 0);
        var bayern = QuoteFixtures.finishedMatch("560902", NEXT_WEEK, BAYERN, DORTMUND, 4, 0);
        return List.of(
                stats().forPlayer(NDIAYE).inMatch(everton).minutes(90).goals(3).tackles(1).build(),
                stats().forPlayer(BETO).inMatch(everton).minutes(90).goals(0).tackles(1).build(),
                stats().forPlayer(ZIRKZEE).inMatch(everton).minutes(90).goals(1).tackles(0).build(),
                stats().forPlayer(SAKA).inMatch(arsenal).minutes(90).goals(2).tackles(2).build(),
                stats().forPlayer(KANE).inMatch(bayern).minutes(90).goals(4).tackles(3).build());
    }

    private static StrategyVersion requoteStrategy(long id, String name, WeightSet defaultWeights) {
        return new StrategyVersion(id, id, name, 1, BigDecimal.TEN, new BigDecimal("10.00"), defaultWeights,
                Map.of(), Instant.parse("2026-08-01T00:00:00Z"));
    }

    /**
     * La semana del ejemplo del spec: Ndiaye queda con goles +0,8, asistencias +0,4, tiros y pases
     * clave 0, regates y quites −0,4 y rating +0,6, que con la estrategia A dan un score de 0,30.
     * Kane, de la Bundesliga, es par de los delanteros de la Premier.
     */
    private static List<PlayerMatchStats> specExampleWeek() {
        return List.of(
                stats().forPlayer(NDIAYE).inMatch(EVERTON_MAN_UTD).minutes(90).goals(3).assists(1)
                        .successfulDribbles(1).tackles(1).rating("8.00").build(),
                stats().forPlayer(BETO).inMatch(EVERTON_MAN_UTD).minutes(90).goals(2).assists(2)
                        .rating("8.00").build(),
                stats().forPlayer(ZIRKZEE).inMatch(EVERTON_MAN_UTD).minutes(90).goals(1)
                        .successfulDribbles(2).tackles(2).rating("7.00").build(),
                stats().forPlayer(SAKA).inMatch(ARSENAL_LIVERPOOL).minutes(90)
                        .successfulDribbles(2).tackles(2).rating("6.00").build(),
                stats().forPlayer(KANE).inMatch(BAYERN_DORTMUND).minutes(90)
                        .successfulDribbles(2).tackles(2).rating("6.50").build());
    }

    /**
     * La semana siguiente solo hay goles: Ndiaye no juega (0 goles, −0,8) y los demás convierten 1,
     * 2, 3 y 4. Con la estrategia A, Ndiaye queda en −0,20.
     */
    private static List<PlayerMatchStats> ndiayeScoresNoGoals() {
        var everton = QuoteFixtures.finishedMatch("560800", NEXT_WEEK, EVERTON, MAN_UTD, 3, 2);
        var arsenal = QuoteFixtures.finishedMatch("560801", NEXT_WEEK, QuoteFixtures.ARSENAL,
                QuoteFixtures.LIVERPOOL, 3, 0);
        var bayern = QuoteFixtures.finishedMatch("560802", NEXT_WEEK, BAYERN, DORTMUND, 4, 0);
        return List.of(
                stats().forPlayer(BETO).inMatch(everton).minutes(90).goals(1).build(),
                stats().forPlayer(ZIRKZEE).inMatch(everton).minutes(90).goals(2).build(),
                stats().forPlayer(SAKA).inMatch(arsenal).minutes(90).goals(3).build(),
                stats().forPlayer(KANE).inMatch(bayern).minutes(90).goals(4).build());
    }

    private static Quote calculatedOf(List<Quote> quotes, Player player) {
        return quotes.stream()
                .filter(quote -> quote.playerId() == player.id() && quote.type() == QuoteType.CALCULATED)
                .findFirst()
                .orElseThrow();
    }

    private static Map<Long, Quote> lastByPlayer(List<Quote> quotes) {
        return quotes.stream()
                .filter(quote -> quote.type() == QuoteType.CALCULATED)
                .collect(Collectors.toMap(Quote::playerId, Function.identity()));
    }

    private static Quote previousQuote(Player player, String value) {
        return QuoteFixtures.calculated(player.id(), WEEK.previous(), value, value, "0.000000", STRATEGY_A);
    }

    private static StrategyVersion strategy(WeightSet defaultWeights, Map<Position, WeightSet> positionWeights) {
        return new StrategyVersion(9L, 9L, "Prueba", 1, BigDecimal.TEN, new BigDecimal("10.00"), defaultWeights,
                positionWeights, Instant.parse("2026-08-01T00:00:00Z"));
    }
}
