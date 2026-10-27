package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersion;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Datos de prueba de la cotización, sin Spring, compartidos por los tests del modelo y del
 * servicio.
 *
 * <p>La semana de referencia es la del lunes 17/08/2026 (la del ejemplo del spec: la inicial de
 * 100,00 rige desde el 17/08 y la calculada de 103,00 desde el 24/08). Hay jugadores de las cuatro
 * posiciones en dos ligas, porque los pares de una posición juntan las cinco ligas. Los partidos se
 * juegan el sábado de su semana a las 15:00 de Argentina.</p>
 */
public final class QuoteFixtures {
    /**
     * Va primero: los partidos de abajo la usan al inicializarse.
     */
    private static final Duration SATURDAY_AFTERNOON = Duration.ofDays(5).plusHours(15);

    public static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    public static final BigDecimal ZERO_SCORE = new BigDecimal("0.000000");

    public static final Season PREMIER_SEASON = new Season(1L, "2502", League.PREMIER,
            LocalDate.of(2026, 8, 14), LocalDate.of(2027, 5, 23), 1);
    public static final Season BUNDESLIGA_SEASON = new Season(2L, "2503", League.BUNDESLIGA,
            LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 15), 1);

    public static final Team EVERTON = new Team(1L, "62", "Everton FC", null, League.PREMIER);
    public static final Team MAN_UTD = new Team(2L, "66", "Manchester United FC", null, League.PREMIER);
    public static final Team ARSENAL = new Team(3L, "57", "Arsenal FC", null, League.PREMIER);
    public static final Team LIVERPOOL = new Team(4L, "64", "Liverpool FC", null, League.PREMIER);
    public static final Team BAYERN = new Team(5L, "5", "FC Bayern München", null, League.BUNDESLIGA);
    public static final Team DORTMUND = new Team(6L, "4", "Borussia Dortmund", null, League.BUNDESLIGA);

    public static final Player NDIAYE = player(101L, "Iliman Ndiaye", Position.FORWARD, EVERTON);
    public static final Player BETO = player(102L, "Beto", Position.FORWARD, EVERTON);
    public static final Player ZIRKZEE = player(103L, "Joshua Zirkzee", Position.FORWARD, MAN_UTD);
    public static final Player SAKA = player(104L, "Bukayo Saka", Position.FORWARD, ARSENAL);
    public static final Player KANE = player(105L, "Harry Kane", Position.FORWARD, BAYERN);
    public static final Player GREALISH = player(201L, "Jack Grealish", Position.MIDFIELDER, EVERTON);
    public static final Player FERNANDES = player(202L, "Bruno Fernandes", Position.MIDFIELDER, MAN_UTD);
    public static final Player TARKOWSKI = player(301L, "James Tarkowski", Position.DEFENDER, EVERTON);
    public static final Player MAGUIRE = player(302L, "Harry Maguire", Position.DEFENDER, MAN_UTD);
    public static final Player PICKFORD = player(401L, "Jordan Pickford", Position.GOALKEEPER, EVERTON);
    public static final Player BAYINDIR = player(402L, "Altay Bayındır", Position.GOALKEEPER, MAN_UTD);

    /**
     * Everton 2 - 1 Manchester United, el sábado de {@link #WEEK}.
     */
    public static final Match EVERTON_MAN_UTD = finishedMatch("560600", WEEK, EVERTON, MAN_UTD, 2, 1);

    /**
     * Arsenal 0 - 0 Liverpool, el sábado de {@link #WEEK}.
     */
    public static final Match ARSENAL_LIVERPOOL = finishedMatch("560601", WEEK, ARSENAL, LIVERPOOL, 0, 0);

    /**
     * La estrategia A de arranque, guardada como la versión 1 con id 1.
     */
    public static final StrategyVersion STRATEGY_A = saved(StartingStrategies.MATCH_PERFORMANCE, 1L);

    /**
     * La estrategia B de arranque, guardada como la versión 1 con id 2.
     */
    public static final StrategyVersion STRATEGY_B = saved(StartingStrategies.BALANCED_BY_POSITION, 2L);

    private QuoteFixtures() {
    }

    public static Player player(long id, String name, Position position, Team team) {
        return new Player(id, "ext-" + id, name, position, team);
    }

    public static Player inactive(Player player) {
        return player.deactivate();
    }

    /**
     * Un partido terminado con sus estadísticas ya cargadas, el sábado de esa semana. El ganador
     * sale del resultado.
     */
    public static Match finishedMatch(String externalId, QuoteWeek week, Team home, Team away, int homeGoals,
                                      int awayGoals) {
        return finishedMatch(externalId, week.start().plus(SATURDAY_AFTERNOON), home, away, homeGoals, awayGoals,
                true);
    }

    /**
     * Un partido terminado al que todavía le faltan las estadísticas ({@code needsStats()}).
     */
    public static Match finishedMatchWithoutStats(String externalId, QuoteWeek week, Team home, Team away) {
        return finishedMatch(externalId, week.start().plus(SATURDAY_AFTERNOON), home, away, 1, 1, false);
    }

    public static Match finishedMatch(String externalId, Instant kickOff, Team home, Team away, int homeGoals,
                                      int awayGoals, boolean statsLoaded) {
        Season season = home.league() == League.BUNDESLIGA ? BUNDESLIGA_SEASON : PREMIER_SEASON;
        return new Match(null, externalId, season, kickOff, 1, MatchStatus.FINISHED, home, away,
                new Score(homeGoals, awayGoals), new Score(0, 0), winner(homeGoals, awayGoals), null, statsLoaded);
    }

    /**
     * Un partido programado de esa semana, que todavía no se jugó.
     */
    public static Match scheduledMatch(String externalId, QuoteWeek week, Team home, Team away) {
        Season season = home.league() == League.BUNDESLIGA ? BUNDESLIGA_SEASON : PREMIER_SEASON;
        return new Match(null, externalId, season, week.start().plus(SATURDAY_AFTERNOON), 1, MatchStatus.TIMED,
                home, away, new Score(null, null), new Score(null, null), null);
    }

    public static Quote initial(long playerId, QuoteWeek week, StrategyVersion strategy) {
        return new Quote(null, playerId, week, week.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategy.ref());
    }

    public static Quote calculated(long playerId, QuoteWeek week, String value, String baseValue, String score,
                                   StrategyVersion strategy) {
        return new Quote(null, playerId, week, week.end(), new BigDecimal(value), new BigDecimal(baseValue),
                new BigDecimal(score), QuoteType.CALCULATED, strategy.ref());
    }

    public static Quote requoted(long playerId, QuoteWeek week, Instant validFrom, String value, String baseValue,
                                 String score, StrategyVersion strategy) {
        return new Quote(null, playerId, week, validFrom, new BigDecimal(value), new BigDecimal(baseValue),
                new BigDecimal(score), QuoteType.REQUOTED, strategy.ref());
    }

    private static StrategyVersion saved(StartingStrategies.Definition definition, long id) {
        return new StrategyVersion(id, id, definition.name(), 1, definition.scaleFactor(), definition.minimumValue(),
                definition.defaultWeights(), definition.positionWeights(), Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static MatchWinner winner(int homeGoals, int awayGoals) {
        if (homeGoals > awayGoals) {
            return MatchWinner.HOME_TEAM;
        }
        return homeGoals < awayGoals ? MatchWinner.AWAY_TEAM : MatchWinner.DRAW;
    }
}
