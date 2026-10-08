package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Datos de prueba de la sincronización, compartidos por los tests de modelo, de servicio, de
 * integración y end to end.
 *
 * <p>{@link #snapshot(League)} devuelve una liga chica y válida: una temporada, dos equipos con
 * jugadores de las cuatro posiciones y dos partidos entre ellos, uno {@code FINISHED} con
 * resultado y uno {@code TIMED} sin resultado.</p>
 *
 * <p>La Premier usa los datos reales de {@code contracts/football-data-api.md}: la temporada 2502,
 * Liverpool FC y Chelsea FC, Alisson Becker, Kostas Tsimikas, Federico Chiesa y Mahdi
 * Nicoll-Jazuli, que la fuente informa sin posición. Las otras ligas usan equipos reales con
 * temporadas, jugadores y partidos inventados. Ningún id se repite entre ligas.</p>
 */
public final class SnapshotFixtures {

    public static final LocalDate SEASON_START = LocalDate.of(2026, 8, 21);
    public static final LocalDate SEASON_END = LocalDate.of(2027, 5, 30);
    public static final int CURRENT_MATCHDAY = 6;
    public static final Instant FINISHED_KICK_OFF = Instant.parse("2026-08-22T14:00:00Z");
    public static final Instant TIMED_KICK_OFF = Instant.parse("2026-10-17T14:00:00Z");

    public static final String PREMIER_SEASON_ID = "2502";
    public static final String LIVERPOOL_ID = "64";
    public static final String LIVERPOOL_NAME = "Liverpool FC";
    public static final String LIVERPOOL_CREST = "https://crests.football-data.org/64.png";
    public static final String CHELSEA_ID = "61";
    public static final String CHELSEA_NAME = "Chelsea FC";
    public static final String CHELSEA_CREST = "https://crests.football-data.org/61.png";
    public static final String ALISSON_ID = "1795";
    public static final String TSIMIKAS_ID = "7383";
    public static final String CHIESA_ID = "1780";
    public static final String NO_POSITION_PLAYER_ID = "301113";
    public static final String NO_POSITION_PLAYER_NAME = "Mahdi Nicoll-Jazuli";
    public static final String BAYERN_ID = "5";
    public static final String BAYERN_NAME = "FC Bayern München";
    public static final String JUVENTUS_ID = "109";
    public static final String JUVENTUS_NAME = "Juventus FC";

    private static final String FINISHED_MATCH_SUFFIX = "01";
    private static final String TIMED_MATCH_SUFFIX = "02";
    private static final LocalDate GENERATED_BIRTH_DATE = LocalDate.of(1998, 3, 15);

    private SnapshotFixtures() {
    }

    public static LeagueSnapshot snapshot(League league) {
        return switch (league) {
            case PREMIER -> withTwoTeams(league, liverpool(), chelsea());
            case BUNDESLIGA -> withTwoTeams(league, generatedTeam(BAYERN_ID, BAYERN_NAME, "Germany"),
                    generatedTeam("4", "Borussia Dortmund", "Germany"));
            case LA_LIGA -> withTwoTeams(league, generatedTeam("86", "Real Madrid CF", "Spain"),
                    generatedTeam("81", "FC Barcelona", "Spain"));
            case SERIE_A -> withTwoTeams(league, generatedTeam(JUVENTUS_ID, JUVENTUS_NAME, "Italy"),
                    generatedTeam("108", "FC Internazionale Milano", "Italy"));
            case LIGUE_1 -> withTwoTeams(league, generatedTeam("524", "Paris Saint-Germain FC", "France"),
                    generatedTeam("516", "Olympique de Marseille", "France"));
        };
    }

    /**
     * Las cinco ligas, en el orden del enum.
     */
    public static List<LeagueSnapshot> allSnapshots() {
        return Arrays.stream(League.values()).map(SnapshotFixtures::snapshot).toList();
    }

    /**
     * Una variante de la liga con la temporada de {@link #snapshot(League)} y los equipos y partidos
     * recibidos.
     */
    public static LeagueSnapshot snapshot(League league, List<TeamSnapshot> teams, List<MatchSnapshot> matches) {
        return new LeagueSnapshot(league, season(league, seasonId(league)), teams, matches);
    }

    public static LeagueSnapshot snapshot(League league, TeamSnapshot... teams) {
        return snapshot(league, List.of(teams), List.of());
    }

    public static String seasonId(League league) {
        return switch (league) {
            case PREMIER -> PREMIER_SEASON_ID;
            case BUNDESLIGA -> "2503";
            case LA_LIGA -> "2504";
            case SERIE_A -> "2505";
            case LIGUE_1 -> "2506";
        };
    }

    public static String finishedMatchId(League league) {
        return seasonId(league) + FINISHED_MATCH_SUFFIX;
    }

    public static String timedMatchId(League league) {
        return seasonId(league) + TIMED_MATCH_SUFFIX;
    }

    public static TeamSnapshot liverpool() {
        return new TeamSnapshot(LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST,
                List.of(alisson(), tsimikas(), generatedPlayer(LIVERPOOL_ID, LIVERPOOL_NAME, Position.MIDFIELDER,
                        "England"), chiesa()));
    }

    /**
     * Cuatro jugadores generados, uno por posición, y Mahdi Nicoll-Jazuli, sin posición.
     */
    public static TeamSnapshot chelsea() {
        List<PlayerSnapshot> squad = new ArrayList<>(generatedSquad(CHELSEA_ID, CHELSEA_NAME, "England"));
        squad.add(noPositionPlayer());
        return new TeamSnapshot(CHELSEA_ID, CHELSEA_NAME, CHELSEA_CREST, squad);
    }

    public static PlayerSnapshot alisson() {
        return new PlayerSnapshot(ALISSON_ID, "Alisson Becker", Position.GOALKEEPER, LocalDate.of(1992, 10, 2),
                "Brazil");
    }

    public static PlayerSnapshot tsimikas() {
        return new PlayerSnapshot(TSIMIKAS_ID, "Kostas Tsimikas", Position.DEFENDER, LocalDate.of(1996, 5, 12),
                "Greece");
    }

    public static PlayerSnapshot chiesa() {
        return new PlayerSnapshot(CHIESA_ID, "Federico Chiesa", Position.FORWARD, LocalDate.of(1997, 10, 25),
                "Italy");
    }

    public static PlayerSnapshot noPositionPlayer() {
        return new PlayerSnapshot(NO_POSITION_PLAYER_ID, NO_POSITION_PLAYER_NAME, null, LocalDate.of(2010, 1, 6),
                "England");
    }

    public static Season season(League league, String externalId) {
        return new Season(null, externalId, league, SEASON_START, SEASON_END, CURRENT_MATCHDAY);
    }

    public static TeamSnapshot team(String externalId, String name, PlayerSnapshot... squad) {
        return new TeamSnapshot(externalId, name, null, List.of(squad));
    }

    public static PlayerSnapshot player(String externalId, String name, Position position) {
        return new PlayerSnapshot(externalId, name, position, null, null);
    }

    /**
     * Un partido sin resultado, como los que todavía no se jugaron.
     */
    public static MatchSnapshot match(String externalId, String seasonExternalId, MatchStatus status,
                                      String homeTeamExternalId, String awayTeamExternalId) {
        return new MatchSnapshot(externalId, seasonExternalId, TIMED_KICK_OFF, CURRENT_MATCHDAY + 1, status,
                homeTeamExternalId, awayTeamExternalId, null, null, null);
    }

    /**
     * Un partido {@code FINISHED} que ganó el local 2 a 1, 1 a 0 en el primer tiempo.
     */
    public static MatchSnapshot finishedMatch(String externalId, String seasonExternalId, String homeTeamExternalId,
                                              String awayTeamExternalId) {
        return new MatchSnapshot(externalId, seasonExternalId, FINISHED_KICK_OFF, 1, MatchStatus.FINISHED,
                homeTeamExternalId, awayTeamExternalId, new Score(2, 1), new Score(1, 0), MatchWinner.HOME_TEAM);
    }

    /**
     * El equipo del snapshot como si ya estuviera guardado con ese id.
     */
    public static Team savedTeam(long id, TeamSnapshot team, League league) {
        return new Team(id, team.externalId(), team.name(), team.crest(), league);
    }

    /**
     * El jugador del snapshot como si ya estuviera guardado con ese id, activo y en ese equipo. El
     * snapshot tiene que traer nombre y posición.
     */
    public static Player savedPlayer(long id, PlayerSnapshot player, Team team) {
        return new Player(id, player.externalId(), player.name(), player.position(), team, player.dateOfBirth(),
                player.nationality(), true);
    }

    private static LeagueSnapshot withTwoTeams(League league, TeamSnapshot first, TeamSnapshot second) {
        String seasonId = seasonId(league);
        return snapshot(league, List.of(first, second), List.of(
                finishedMatch(finishedMatchId(league), seasonId, first.externalId(), second.externalId()),
                match(timedMatchId(league), seasonId, MatchStatus.TIMED, second.externalId(), first.externalId())));
    }

    private static TeamSnapshot generatedTeam(String externalId, String name, String nationality) {
        return new TeamSnapshot(externalId, name, null, generatedSquad(externalId, name, nationality));
    }

    private static List<PlayerSnapshot> generatedSquad(String teamExternalId, String teamName, String nationality) {
        return Arrays.stream(Position.values())
                .map(position -> generatedPlayer(teamExternalId, teamName, position, nationality))
                .toList();
    }

    /**
     * El id es el del equipo seguido de la posición ({@code 61001} es el arquero de Chelsea FC), así
     * no se repite entre equipos ni con los jugadores reales.
     */
    private static PlayerSnapshot generatedPlayer(String teamExternalId, String teamName, Position position,
                                                  String nationality) {
        String externalId = teamExternalId + "00" + (position.ordinal() + 1);
        return new PlayerSnapshot(externalId, positionLabel(position) + " de " + teamName, position,
                GENERATED_BIRTH_DATE, nationality);
    }

    private static String positionLabel(Position position) {
        return switch (position) {
            case GOALKEEPER -> "Arquero";
            case DEFENDER -> "Defensor";
            case MIDFIELDER -> "Mediocampista";
            case FORWARD -> "Delantero";
        };
    }
}
