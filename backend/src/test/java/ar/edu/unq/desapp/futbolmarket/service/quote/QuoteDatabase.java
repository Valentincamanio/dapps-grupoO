package ar.edu.unq.desapp.futbolmarket.service.quote;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerMatchStatsBuilder;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.QuoteMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.QuoteSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;

/**
 * Carga en la H2 de un test de integración lo que el recálculo lee: una temporada de la Premier,
 * equipos, jugadores, partidos terminados y sus estadísticas. También lee y borra las
 * cotizaciones, cosa que el repository no ofrece a propósito (FR-005).
 *
 * <p>No es un bean: cada test lo arma con su contexto. Los partidos se juegan el sábado de su
 * semana a las 15:00 de Argentina, como en {@code QuoteFixtures}.</p>
 */
public final class QuoteDatabase {
    private static final Duration SATURDAY_AFTERNOON = Duration.ofDays(5).plusHours(15);

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatsRepository statsRepository;
    private final QuoteSQLDAO quoteDAO;
    private final PlayerMatchStatsSQLDAO statsDAO;
    private final MatchSQLDAO matchDAO;
    private final SeasonSQLDAO seasonDAO;
    private final PlayerSQLDAO playerDAO;
    private final TeamSQLDAO teamDAO;
    private final QuoteMapper quoteMapper;
    private final TransactionTemplate transactionTemplate;

    private Season season;

    public QuoteDatabase(ApplicationContext context) {
        seasonRepository = context.getBean(SeasonRepository.class);
        teamRepository = context.getBean(TeamRepository.class);
        playerRepository = context.getBean(PlayerRepository.class);
        matchRepository = context.getBean(MatchRepository.class);
        statsRepository = context.getBean(PlayerMatchStatsRepository.class);
        quoteDAO = context.getBean(QuoteSQLDAO.class);
        statsDAO = context.getBean(PlayerMatchStatsSQLDAO.class);
        matchDAO = context.getBean(MatchSQLDAO.class);
        seasonDAO = context.getBean(SeasonSQLDAO.class);
        playerDAO = context.getBean(PlayerSQLDAO.class);
        teamDAO = context.getBean(TeamSQLDAO.class);
        quoteMapper = context.getBean(QuoteMapper.class);
        transactionTemplate = context.getBean(TransactionTemplate.class);
    }

    /**
     * Borra todo lo que carga, en el orden de las claves foráneas. Las estrategias sembradas quedan.
     */
    public void clean() {
        quoteDAO.deleteAll();
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        season = null;
    }

    public Team team(String externalId, String name) {
        return teamRepository.save(new Team(null, externalId, name, null, League.PREMIER, "ws-" + externalId));
    }

    public Player player(String externalId, String name, Position position, Team team) {
        return playerRepository.save(new Player(externalId, name, position, team));
    }

    public Player inactive(Player player) {
        return playerRepository.save(player.deactivate());
    }

    /**
     * Un partido terminado el sábado de esa semana; el ganador sale del resultado.
     */
    public Match finishedMatch(String externalId, QuoteWeek week, Team home, Team away, int homeGoals, int awayGoals,
                               boolean statsLoaded) {
        return matchRepository.saveAll(List.of(new Match(null, externalId, season(), week.start().plus(SATURDAY_AFTERNOON),
                1, MatchStatus.FINISHED, home, away, new Score(homeGoals, awayGoals), new Score(0, 0),
                winner(homeGoals, awayGoals), null, statsLoaded))).getFirst();
    }

    /**
     * Un partido de esa semana que todavía no se jugó.
     */
    public Match scheduledMatch(String externalId, QuoteWeek week, Team home, Team away) {
        return matchRepository.saveAll(List.of(new Match(null, externalId, season(), week.start().plus(SATURDAY_AFTERNOON),
                1, MatchStatus.TIMED, home, away, new Score(null, null), new Score(null, null), null))).getFirst();
    }

    public void stats(PlayerMatchStatsBuilder... stats) {
        List<PlayerMatchStats> built = List.of(stats).stream().map(PlayerMatchStatsBuilder::build).toList();
        statsRepository.saveAll(built);
    }

    /**
     * Todas las cotizaciones guardadas. El mapper lee la versión, que es lazy: se lee dentro de una
     * transacción.
     */
    public List<Quote> quotes() {
        return transactionTemplate.execute(status -> quoteDAO.findAll().stream().map(quoteMapper::toDomain).toList());
    }

    public List<Quote> quotesOf(Player player) {
        return quotes().stream().filter(quote -> quote.playerId() == player.id()).toList();
    }

    public List<Quote> quotesOf(QuoteWeek week) {
        return quotes().stream().filter(quote -> quote.week().equals(week)).toList();
    }

    public long quoteCount() {
        return quoteDAO.count();
    }

    public void deleteQuote(Quote quote) {
        quoteDAO.deleteById(quote.id());
    }

    private Season season() {
        if (season == null) {
            season = seasonRepository.save(new Season(null, "2502", League.PREMIER, LocalDate.of(2026, 8, 14),
                    LocalDate.of(2027, 5, 23), 1));
        }
        return season;
    }

    private static MatchWinner winner(int homeGoals, int awayGoals) {
        if (homeGoals > awayGoals) {
            return MatchWinner.HOME_TEAM;
        }
        return homeGoals < awayGoals ? MatchWinner.AWAY_TEAM : MatchWinner.DRAW;
    }
}
