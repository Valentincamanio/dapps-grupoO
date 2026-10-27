package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.CardKind;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedCard;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedGoal;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Datos de prueba de la sincronización de WhoScored, sin Spring, compartidos por los tests del
 * modelo, del servicio y de punta a punta.
 *
 * <p>La Premier 2026/27 con seis equipos que juegan una doble rueda: diez fechas, tres partidos por
 * fecha y diez partidos por equipo. Las fechas 0 a 6 están {@code FINISHED} y las 7 a 9,
 * {@code TIMED}. El tercer partido de cada fecha es nocturno (23:30 UTC), así su fecha en la hora de
 * Londres, que es la que muestra WhoScored, es la del día siguiente.</p>
 *
 * <p>La segunda rueda no es un espejo de la primera: tiene otro orden de rivales y la localía
 * invertida, como un calendario real. En un espejo, un equipo equivocado que coincide de lado en
 * cuatro fechas de la primera rueda coincidiría en ocho, con los mismos pares de rivales, y la
 * correspondencia de rivales no lo detectaría.</p>
 *
 * <p>Del lado de WhoScored, los mismos equipos con sus ids y sus nombres cortos: "Man Utd" y
 * "Tottenham" solo se pueden cruzar por calendario. {@link #EVERTON_MAN_UTD_STATS} es el Everton -
 * Manchester United de la fecha 1 con 2 minutos agregados en el primer tiempo y 4 en el segundo
 * ({@code endMinute} 96), con las apariciones de los escenarios 4 a 7 de la HU1.</p>
 */
public final class StatsFixtures {

    public static final League LEAGUE = League.PREMIER;
    public static final String SEASON_ID = "2502";
    public static final LocalDate SEASON_START = LocalDate.of(2026, 8, 21);
    public static final LocalDate SEASON_END = LocalDate.of(2027, 5, 30);
    public static final Season SEASON = new Season(1L, SEASON_ID, LEAGUE, SEASON_START, SEASON_END, 8);
    public static final ZoneId LONDON = ZoneId.of("Europe/London");
    public static final int END_MINUTE = 96;

    public static final Team MAN_UTD = new Team(1L, "66", "Manchester United FC", null, LEAGUE);
    public static final Team EVERTON = new Team(2L, "62", "Everton FC", null, LEAGUE);
    public static final Team BRENTFORD = new Team(3L, "402", "Brentford FC", null, LEAGUE);
    public static final Team TOTTENHAM = new Team(4L, "73", "Tottenham Hotspur FC", null, LEAGUE);
    public static final Team ARSENAL = new Team(5L, "57", "Arsenal FC", null, LEAGUE);
    public static final Team LIVERPOOL = new Team(6L, "64", "Liverpool FC", null, LEAGUE);

    public static final ReportedTeam MAN_UTD_WS = new ReportedTeam("32", "Man Utd");
    public static final ReportedTeam EVERTON_WS = new ReportedTeam("31", "Everton");
    public static final ReportedTeam BRENTFORD_WS = new ReportedTeam("189", "Brentford");
    public static final ReportedTeam TOTTENHAM_WS = new ReportedTeam("30", "Tottenham");
    public static final ReportedTeam ARSENAL_WS = new ReportedTeam("13", "Arsenal");
    public static final ReportedTeam LIVERPOOL_WS = new ReportedTeam("26", "Liverpool");

    private static final List<Team> TEAMS = List.of(MAN_UTD, EVERTON, BRENTFORD, TOTTENHAM, ARSENAL, LIVERPOOL);
    private static final List<ReportedTeam> SOURCE_TEAMS =
            List.of(MAN_UTD_WS, EVERTON_WS, BRENTFORD_WS, TOTTENHAM_WS, ARSENAL_WS, LIVERPOOL_WS);

    /**
     * Local y visitante de cada partido, por su posición en {@link #TEAMS}, fecha por fecha. Las
     * fechas 0 a 4 son la primera rueda y las 5 a 9, la segunda.
     */
    private static final int[][][] ROUNDS = {
            {{0, 5}, {1, 4}, {2, 3}},
            {{1, 0}, {2, 5}, {3, 4}},
            {{0, 2}, {3, 1}, {4, 5}},
            {{3, 0}, {4, 2}, {5, 1}},
            {{0, 4}, {5, 3}, {1, 2}},
            {{5, 0}, {2, 4}, {1, 3}},
            {{2, 0}, {1, 5}, {4, 3}},
            {{0, 1}, {3, 2}, {5, 4}},
            {{0, 3}, {4, 1}, {5, 2}},
            {{4, 0}, {3, 5}, {2, 1}}
    };
    private static final LocalDate FIRST_MATCHDAY = LocalDate.of(2026, 8, 22);
    private static final List<LocalTime> KICK_OFF_TIMES =
            List.of(LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(23, 30));
    private static final int LAST_FINISHED_ROUND = 6;
    private static final int FIRST_MATCH_ID = 560600;
    private static final int FIRST_WHOSCORED_MATCH_ID = 1983600;
    private static final List<ScheduledMatch> SCHEDULE = schedule();

    public static final Player PICKFORD = player(101L, "3204", "Jordan Pickford", Position.GOALKEEPER, EVERTON,
            LocalDate.of(1994, 3, 7)).crossWith("93145");
    public static final Player TARKOWSKI = player(102L, "3205", "James Tarkowski", Position.DEFENDER, EVERTON,
            LocalDate.of(1992, 11, 19));
    public static final Player NDIAYE = player(103L, "8135", "Iliman Ndiaye", Position.FORWARD, EVERTON,
            LocalDate.of(2000, 3, 6));
    public static final Player GREALISH = player(104L, "3501", "Jack Grealish", Position.MIDFIELDER, EVERTON,
            LocalDate.of(1995, 9, 10));
    public static final Player BETO = player(105L, "8136", "Beto", Position.FORWARD, EVERTON,
            LocalDate.of(1998, 1, 31));
    public static final Player BARRY = player(106L, "8137", "Thierno Barry", Position.FORWARD, EVERTON,
            LocalDate.of(2002, 10, 21));
    public static final Player OBRIEN = player(107L, "8138", "Jake O'Brien", Position.DEFENDER, EVERTON,
            LocalDate.of(2001, 5, 15));
    public static final Player OBRIEN_WITHOUT_BIRTH_DATE = player(108L, "8139", "Jake O'Brien", Position.DEFENDER,
            EVERTON, null);
    public static final Player CASEMIRO = player(201L, "3255", "Casemiro", Position.MIDFIELDER, MAN_UTD,
            LocalDate.of(1992, 2, 23));
    public static final Player MARTINEZ = player(202L, "3256", "Lisandro Martínez", Position.DEFENDER, MAN_UTD,
            LocalDate.of(1998, 1, 18));
    public static final Player MAGUIRE = player(203L, "3257", "Harry Maguire", Position.DEFENDER, MAN_UTD,
            LocalDate.of(1993, 3, 5));
    public static final Player FERNANDES = player(204L, "3258", "Bruno Fernandes", Position.MIDFIELDER, MAN_UTD,
            LocalDate.of(1994, 9, 8));
    public static final Player BAYINDIR = player(205L, "3259", "Altay Bayındır", Position.GOALKEEPER, MAN_UTD,
            LocalDate.of(1998, 4, 14));
    public static final Player ZIRKZEE = player(206L, "3260", "Joshua Zirkzee", Position.FORWARD, MAN_UTD,
            LocalDate.of(2001, 5, 22));
    public static final Player LEON = player(207L, "3261", "Diego León", Position.DEFENDER, MAN_UTD,
            LocalDate.of(2007, 4, 3));
    public static final Player LEON_HOMONYM = player(208L, "3262", "Diego León", Position.DEFENDER, MAN_UTD,
            LocalDate.of(2004, 1, 10));
    public static final Player WISSA = player(301L, "3401", "Yoane Wissa", Position.FORWARD, BRENTFORD,
            LocalDate.of(1996, 9, 3));
    public static final Player DAVIES = player(401L, "3601", "Ben Davies", Position.DEFENDER, TOTTENHAM,
            LocalDate.of(1993, 4, 24));
    public static final Player DAVIES_HOMONYM = player(402L, "3602", "Ben Davies", Position.DEFENDER, TOTTENHAM,
            LocalDate.of(1993, 8, 11));
    public static final Player DANILO_ARSENAL = player(501L, "3701", "Danilo", Position.MIDFIELDER, ARSENAL,
            LocalDate.of(1999, 2, 11));
    public static final Player DANILO_LIVERPOOL = player(601L, "3801", "Danilo", Position.MIDFIELDER, LIVERPOOL,
            LocalDate.of(1999, 6, 20));

    private static final List<Player> PLAYERS = List.of(PICKFORD, TARKOWSKI, NDIAYE, GREALISH, BETO, BARRY, OBRIEN,
            OBRIEN_WITHOUT_BIRTH_DATE, CASEMIRO, MARTINEZ, MAGUIRE, FERNANDES, BAYINDIR, ZIRKZEE, LEON, LEON_HOMONYM,
            WISSA, DAVIES, DAVIES_HOMONYM, DANILO_ARSENAL, DANILO_LIVERPOOL);

    /**
     * El día del Everton - Manchester United de la fecha 1, del que salen las edades de las
     * apariciones.
     */
    private static final LocalDate EVERTON_MAN_UTD_DAY = FIRST_MATCHDAY.plusWeeks(1);

    public static final ReportedAppearance PICKFORD_APPEARANCE = starter(PICKFORD, "93145", Side.HOME, null);
    public static final ReportedAppearance TARKOWSKI_APPEARANCE = starter(TARKOWSKI, "92556", Side.HOME, null);
    public static final ReportedAppearance NDIAYE_APPEARANCE = new ReportedAppearance("393485", NDIAYE.name(),
            ageOf(NDIAYE), Side.HOME, true, null, null, ndiayeSourceCounts(), new BigDecimal("7.65"), true);
    public static final ReportedAppearance GREALISH_APPEARANCE = starter(GREALISH, "93353", Side.HOME, null);
    public static final ReportedAppearance BETO_APPEARANCE = starter(BETO, "368203", Side.HOME, 72);
    public static final ReportedAppearance BARRY_APPEARANCE = substitute(BARRY, "403590", Side.HOME, 72);
    public static final ReportedAppearance CASEMIRO_APPEARANCE = starter(CASEMIRO, "75577", Side.AWAY, null);
    public static final ReportedAppearance MARTINEZ_APPEARANCE = starter(MARTINEZ, "334380", Side.AWAY, null);
    public static final ReportedAppearance MAGUIRE_APPEARANCE = starter(MAGUIRE, "95408", Side.AWAY, null);
    public static final ReportedAppearance FERNANDES_APPEARANCE = starter(FERNANDES, "123761", Side.AWAY, null);
    public static final ReportedAppearance BAYINDIR_APPEARANCE = substitute(BAYINDIR, "301123", Side.AWAY, null);
    public static final ReportedAppearance ZIRKZEE_APPEARANCE = substitute(ZIRKZEE, "343414", Side.AWAY, 93);

    /**
     * Escenario 4: Beto sale en el 70′ del reloj (expandido 72) y entra Barry. Escenario 5: Casemiro
     * es expulsado en el 60′ (expandido 62). Escenario 6: gol de Ndiaye con asistencia de Grealish.
     * Escenario 7: gol en contra de Maguire, que trae asistidor. Además, Martínez ve la doble
     * amarilla, Bayındır ve una amarilla en el banco sin entrar y Zirkzee entra en el agregado.
     */
    public static final ReportedMatchStats EVERTON_MAN_UTD_STATS = new ReportedMatchStats(
            whoscoredIdOf(EVERTON, MAN_UTD), true, EVERTON_WS.whoscoredId(), MAN_UTD_WS.whoscoredId(), END_MINUTE,
            List.of(PICKFORD_APPEARANCE, TARKOWSKI_APPEARANCE, NDIAYE_APPEARANCE, GREALISH_APPEARANCE,
                    BETO_APPEARANCE, BARRY_APPEARANCE, CASEMIRO_APPEARANCE, MARTINEZ_APPEARANCE, MAGUIRE_APPEARANCE,
                    FERNANDES_APPEARANCE, BAYINDIR_APPEARANCE, ZIRKZEE_APPEARANCE),
            List.of(
                    new ReportedGoal("393485", false, "93353"),
                    new ReportedGoal("95408", true, "123761")),
            List.of(
                    new ReportedCard("334380", CardKind.YELLOW, 30),
                    new ReportedCard("301123", CardKind.YELLOW, 50),
                    new ReportedCard("75577", CardKind.RED, 62),
                    new ReportedCard("334380", CardKind.SECOND_YELLOW, 81)));

    private StatsFixtures() {
    }

    /**
     * Los seis equipos del catálogo, sin cruzar.
     */
    public static List<Team> teams() {
        return TEAMS;
    }

    /**
     * Los seis equipos de WhoScored, en el mismo orden que {@link #teams()}.
     */
    public static List<ReportedTeam> sourceTeams() {
        return SOURCE_TEAMS;
    }

    public static ReportedTeam sourceTeamOf(Team team) {
        return SOURCE_TEAMS.get(indexOf(team));
    }

    /**
     * El equipo cruzado con su id de WhoScored.
     */
    public static Team crossed(Team team) {
        return team.crossWith(sourceTeamOf(team).whoscoredId());
    }

    public static List<Team> crossedTeams() {
        return TEAMS.stream().map(StatsFixtures::crossed).toList();
    }

    /**
     * Los 30 partidos de la temporada, sin cruzar y sin estadísticas, con los equipos sin cruzar.
     */
    public static List<Match> seasonMatches() {
        return seasonMatches(TEAMS);
    }

    /**
     * Los 30 partidos de la temporada con estas versiones de los equipos, que se buscan por su id de
     * Football-Data.
     */
    public static List<Match> seasonMatches(List<Team> teams) {
        return SCHEDULE.stream().map(scheduled -> scheduled.toMatch(teams)).toList();
    }

    /**
     * Los 30 partidos con los equipos cruzados, todavía sin su id de WhoScored.
     */
    public static List<Match> crossedSeasonMatches() {
        return seasonMatches(crossedTeams());
    }

    /**
     * El partido de la temporada con ese local y ese visitante.
     */
    public static Match matchBetween(List<Match> matches, Team home, Team away) {
        return matches.stream()
                .filter(match -> match.homeTeam().externalId().equals(home.externalId()))
                .filter(match -> match.awayTeam().externalId().equals(away.externalId()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * El id de WhoScored del partido con ese local y ese visitante.
     */
    public static String whoscoredIdOf(Team home, Team away) {
        return SCHEDULE.stream()
                .filter(scheduled -> scheduled.home() == indexOf(home) && scheduled.away() == indexOf(away))
                .findFirst()
                .orElseThrow()
                .whoscoredId();
    }

    /**
     * El Everton - Manchester United de la fecha 1, terminado, con los equipos y el partido cruzados.
     */
    public static Match evertonManUtd() {
        return matchBetween(crossedSeasonMatches(), EVERTON, MAN_UTD).crossWith(whoscoredIdOf(EVERTON, MAN_UTD));
    }

    /**
     * Las 30 filas de WhoScored, una por partido, con la fecha en la hora de Londres.
     */
    public static List<ReportedFixture> fixtures() {
        return SCHEDULE.stream().map(ScheduledMatch::toFixture).toList();
    }

    /**
     * Las diez filas de la página de fixtures de un equipo de WhoScored.
     */
    public static List<ReportedFixture> fixturesOf(ReportedTeam team) {
        return fixtures().stream()
                .filter(fixture -> team.whoscoredId().equals(fixture.homeTeamWhoscoredId())
                        || team.whoscoredId().equals(fixture.awayTeamWhoscoredId()))
                .toList();
    }

    /**
     * Todos los jugadores del catálogo. Pickford es el único cruzado. Hay homónimos en el Everton
     * (O'Brien, uno sin fecha de nacimiento), en el Manchester United (León, de distinta edad), en el
     * Tottenham (Davies, de la misma edad) y en dos equipos distintos (Danilo, de la misma edad).
     * Wissa figura en el Brentford, aunque WhoScored ya lo muestra en el Tottenham (HU1, escenario 9).
     */
    public static List<Player> players() {
        return PLAYERS;
    }

    /**
     * La edad del jugador el día del Everton - Manchester United, como la informa WhoScored.
     */
    public static Integer ageOf(Player player) {
        if (player.dateOfBirth() == null) {
            return null;
        }
        return Period.between(player.dateOfBirth(), EVERTON_MAN_UTD_DAY).getYears();
    }

    public static SnapshotTeamEntry teamEntry(Team team) {
        return new SnapshotTeamEntry(team.league(), team.externalId(), sourceTeamOf(team).whoscoredId());
    }

    public static SnapshotMatchEntry matchEntry(Team home, Team away) {
        Match match = matchBetween(seasonMatches(), home, away);
        return new SnapshotMatchEntry(match.season().league(), match.externalId(), whoscoredIdOf(home, away));
    }

    /**
     * Una fila de jugador con algunas métricas, el rating y sin la figura.
     */
    public static SnapshotPlayerRow row(Player player, String whoscoredId, int minutesPlayed) {
        MetricCounts counts =
                MetricCounts.of(Map.of(Metric.PASSES, 30, Metric.ACCURATE_PASSES, 25, Metric.TACKLES, 2));
        return new SnapshotPlayerRow(player.externalId(), whoscoredId, true, minutesPlayed, counts,
                new BigDecimal("6.80"), false);
    }

    public static SnapshotStatsEntry statsEntry(Team home, Team away, SnapshotPlayerRow... rows) {
        Match match = matchBetween(seasonMatches(), home, away);
        return new SnapshotStatsEntry(match.season().league(), match.externalId(), Arrays.asList(rows));
    }

    /**
     * Un snapshot chico: el Everton y el Manchester United cruzados, el Everton - Manchester United
     * de la fecha 1 con las estadísticas de Pickford y Casemiro, y el Manchester United - Everton de
     * la fecha 7, todavía sin jugar.
     */
    public static StatsSnapshot snapshot() {
        return new StatsSnapshot(
                List.of(teamEntry(EVERTON), teamEntry(MAN_UTD)),
                List.of(matchEntry(EVERTON, MAN_UTD), matchEntry(MAN_UTD, EVERTON)),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "93145", END_MINUTE),
                        row(CASEMIRO, "75577", 62))));
    }

    private static Player player(Long id, String externalId, String name, Position position, Team team,
                                 LocalDate dateOfBirth) {
        return new Player(id, externalId, name, position, team, dateOfBirth, null, true);
    }

    private static ReportedAppearance starter(Player player, String whoscoredId, Side side, Integer leftAt) {
        return new ReportedAppearance(whoscoredId, player.name(), ageOf(player), side, true, null, leftAt,
                MetricCounts.empty(), null, false);
    }

    private static ReportedAppearance substitute(Player player, String whoscoredId, Side side, Integer enteredAt) {
        return new ReportedAppearance(whoscoredId, player.name(), ageOf(player), side, false, enteredAt, null,
                MetricCounts.empty(), null, false);
    }

    private static MetricCounts ndiayeSourceCounts() {
        return MetricCounts.of(Map.of(Metric.SHOTS, 4, Metric.SHOTS_ON_TARGET, 2, Metric.KEY_PASSES, 1,
                Metric.PASSES, 30, Metric.ACCURATE_PASSES, 25, Metric.SUCCESSFUL_DRIBBLES, 3));
    }

    private static int indexOf(Team team) {
        for (int index = 0; index < TEAMS.size(); index++) {
            if (TEAMS.get(index).externalId().equals(team.externalId())) {
                return index;
            }
        }
        throw new IllegalArgumentException("El equipo no es de los datos de prueba: " + team.name());
    }

    private static List<ScheduledMatch> schedule() {
        List<ScheduledMatch> schedule = new ArrayList<>();
        for (int round = 0; round < ROUNDS.length; round++) {
            for (int slot = 0; slot < ROUNDS[round].length; slot++) {
                schedule.add(new ScheduledMatch(schedule.size(), round, slot, ROUNDS[round][slot][0],
                        ROUNDS[round][slot][1]));
            }
        }
        return List.copyOf(schedule);
    }

    /**
     * Un partido del calendario: su número, su fecha, su horario y los equipos por su posición.
     */
    private record ScheduledMatch(int number, int round, int slot, int home, int away) {
        Instant kickOff() {
            return FIRST_MATCHDAY.plusWeeks(round).atTime(KICK_OFF_TIMES.get(slot)).toInstant(ZoneOffset.UTC);
        }

        boolean finished() {
            return round <= LAST_FINISHED_ROUND;
        }

        String externalId() {
            return String.valueOf(FIRST_MATCH_ID + number);
        }

        String whoscoredId() {
            return String.valueOf(FIRST_WHOSCORED_MATCH_ID + number);
        }

        Match toMatch(List<Team> teams) {
            Team homeTeam = versionOf(teams, TEAMS.get(home));
            Team awayTeam = versionOf(teams, TEAMS.get(away));
            if (!finished()) {
                return new Match(null, externalId(), SEASON, kickOff(), round + 1, MatchStatus.TIMED, homeTeam,
                        awayTeam, null, null, null);
            }
            return new Match(null, externalId(), SEASON, kickOff(), round + 1, MatchStatus.FINISHED, homeTeam,
                    awayTeam, new Score(1, 0), new Score(0, 0), MatchWinner.HOME_TEAM);
        }

        ReportedFixture toFixture() {
            ReportedTeam homeTeam = SOURCE_TEAMS.get(home);
            ReportedTeam awayTeam = SOURCE_TEAMS.get(away);
            return new ReportedFixture(whoscoredId(), LocalDate.ofInstant(kickOff(), LONDON),
                    homeTeam.whoscoredId(), homeTeam.name(), awayTeam.whoscoredId(), awayTeam.name(), finished());
        }

        private static Team versionOf(List<Team> teams, Team team) {
            return teams.stream()
                    .filter(version -> version.externalId().equals(team.externalId()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
